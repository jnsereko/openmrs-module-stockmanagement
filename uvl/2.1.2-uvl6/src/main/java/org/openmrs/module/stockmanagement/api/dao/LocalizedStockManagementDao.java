package org.openmrs.module.stockmanagement.api.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.openmrs.api.ConceptNameType;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.dto.BatchJobDTO;
import org.openmrs.module.stockmanagement.api.dto.BatchJobSearchFilter;
import org.openmrs.module.stockmanagement.api.dto.ConceptNameDTO;
import org.openmrs.module.stockmanagement.api.dto.RecordPrivilegeFilter;
import org.openmrs.module.stockmanagement.api.dto.Result;

/**
 * The 2.1.2 DAO with concept names in the user's locale.
 * <p>
 * 2.1.2's {@code getConceptNames} and {@code getConceptNamesByConceptIds} return every fully
 * specified name of each concept, whatever its locale, and their callers keep the first one. A
 * concept with a fully specified name in English and in French was shown in whichever came first
 * from the database: the dispensing unit « (Capsule) » in the dispense batch list of a French
 * session (UVL-EMR#529). These two methods are the only place the DAO names a concept (units,
 * categories, reasons, items), so this class overrides them to return one name per concept, the
 * one {@link #pickName} prefers for {@link #getLocale()}.
 * <p>
 * It also lists the batch jobs that have no location (UVL-EMR#862): 2.1.2 restricts the list to
 * the locations of the user's reports scope with {@code location_scope in (...)}, which never
 * matches a job whose location is NULL, and every Report job is created without one (the REST
 * resource cannot set it). See {@link #findBatchJobs}.
 * <p>
 * {@code moduleApplicationContext.xml} names this class as the service's DAO.
 */
public class LocalizedStockManagementDao extends StockManagementDao {

	/**
	 * A name the query offers for a concept.
	 */
	static class NameCandidate {

		final Integer conceptId;

		final String name;

		final Locale locale;

		final boolean localePreferred;

		final boolean fullySpecified;

		NameCandidate(Integer conceptId, String name, Locale locale, boolean localePreferred, boolean fullySpecified) {
			this.conceptId = conceptId;
			this.name = name;
			this.locale = locale;
			this.localePreferred = localePreferred;
			this.fullySpecified = fullySpecified;
		}
	}

	@Override
	public Map<Integer, String> getConceptNames(List<Integer> conceptIds) {
		Map<Integer, String> names = new HashMap<>();
		for (ConceptNameDTO name : getConceptNamesByConceptIds(conceptIds)) {
			names.put(name.getConceptId(), name.getName());
		}
		return names;
	}

	@Override
	public List<ConceptNameDTO> getConceptNamesByConceptIds(List<Integer> ids) {
		if (ids == null || ids.isEmpty()) {
			return new ArrayList<>();
		}
		Map<Integer, List<NameCandidate>> byConcept = new LinkedHashMap<>();
		for (NameCandidate candidate : findNameCandidates(ids)) {
			byConcept.computeIfAbsent(candidate.conceptId, id -> new ArrayList<>()).add(candidate);
		}
		Locale locale = getLocale();
		List<ConceptNameDTO> result = new ArrayList<>();
		for (List<NameCandidate> candidates : byConcept.values()) {
			NameCandidate picked = pickName(candidates, locale);
			if (picked == null) {
				continue;
			}
			ConceptNameDTO dto = new ConceptNameDTO();
			dto.setConceptId(picked.conceptId);
			dto.setName(picked.name);
			result.add(dto);
		}
		return result;
	}

	/**
	 * The name of one concept to show in {@code locale}, from its fully specified and locale
	 * preferred names: the preferred, then the fully specified name in the locale, then in its
	 * language, then the first fully specified name (2.1.2's choice). Null if there is none.
	 */
	static NameCandidate pickName(List<NameCandidate> candidates, Locale locale) {
		NameCandidate best = null;
		int bestRank = Integer.MAX_VALUE;
		for (NameCandidate candidate : candidates) {
			int rank = rank(candidate, locale);
			if (rank < bestRank) {
				best = candidate;
				bestRank = rank;
			}
		}
		return best;
	}

	private static int rank(NameCandidate candidate, Locale locale) {
		if (locale != null && candidate.locale != null) {
			if (candidate.locale.equals(locale)) {
				return candidate.localePreferred ? 0 : candidate.fullySpecified ? 1 : 4;
			}
			if (candidate.locale.getLanguage().equals(locale.getLanguage())) {
				return candidate.localePreferred ? 2 : candidate.fullySpecified ? 3 : 4;
			}
		}
		return candidate.fullySpecified ? 4 : Integer.MAX_VALUE;
	}

	/**
	 * The user's locale, as the REST module set it for the request.
	 */
	protected Locale getLocale() {
		return Context.getLocale();
	}

	/**
	 * The unvoided fully specified and locale preferred names of these concepts, oldest first.
	 */
	protected List<NameCandidate> findNameCandidates(List<Integer> ids) {
		List<?> rows = getSession()
		        .createQuery("select cn.concept.conceptId, cn.name, cn.locale, cn.localePreferred, cn.conceptNameType "
		                + "from ConceptName cn where cn.concept.conceptId in (:ids) and cn.voided = false "
		                + "and (cn.conceptNameType = (:cnt) or cn.localePreferred = true) order by cn.conceptNameId")
		        .setParameterList("ids", ids).setParameter("cnt", ConceptNameType.FULLY_SPECIFIED).list();
		List<NameCandidate> candidates = new ArrayList<>();
		for (Object row : rows) {
			Object[] columns = (Object[]) row;
			candidates.add(new NameCandidate(((Number) columns[0]).intValue(), (String) columns[1], (Locale) columns[2],
			        Boolean.TRUE.equals(columns[3]), ConceptNameType.FULLY_SPECIFIED.equals(columns[4])));
		}
		return candidates;
	}

	/**
	 * 2.1.2 keeps the jobs whose location is in the user's scope, so a job without a location is
	 * never listed and cannot be read by its uuid. Here a job without a location is listed for
	 * every user whose reports scope covers at least one location. A request that names a
	 * location, and a call without privilege filters (no scope check), are 2.1.2's. No scope at
	 * all lists nothing.
	 * <p>
	 * The 2.1.2 query cannot say "or null" and its helpers are private, so this reads the jobs
	 * without the location restriction and applies it here, paging afterwards like 2.1.2
	 * ({@code startIndex} is a page number, {@code totalCount} counts the jobs kept). Batch jobs
	 * expire, so the table stays small.
	 */
	@Override
	public Result<BatchJobDTO> findBatchJobs(BatchJobSearchFilter filter,
	        HashSet<RecordPrivilegeFilter> recordPrivilegeFilters) {
		if (recordPrivilegeFilters == null || (filter.getLocationScopeIds() != null && !filter.getLocationScopeIds().isEmpty())) {
			return findBatchJobs212(filter, recordPrivilegeFilters);
		}
		if (recordPrivilegeFilters.isEmpty()) {
			return new Result<>(new ArrayList<>(), 0);
		}

		Integer pageIndex = filter.getStartIndex();
		Integer pageSize = filter.getLimit();
		filter.setStartIndex(null);
		filter.setLimit(null);
		Result<BatchJobDTO> all;
		try {
			all = findBatchJobs212(filter, null);
		}
		finally {
			filter.setStartIndex(pageIndex);
			filter.setLimit(pageSize);
		}

		Set<Integer> scopeLocationIds = recordPrivilegeFilters.stream().map(RecordPrivilegeFilter::getLocationId)
		        .collect(Collectors.toSet());
		List<BatchJobDTO> listed = all.getData().stream()
		        .filter(job -> job.getLocationScopeId() == null || scopeLocationIds.contains(job.getLocationScopeId()))
		        .collect(Collectors.toList());

		long total = listed.size();
		Result<BatchJobDTO> result = new Result<>();
		if (pageIndex != null && pageSize != null && pageIndex >= 0 && pageSize >= 0) {
			result.setPageIndex(pageIndex);
			result.setPageSize(pageSize);
			long from = (long) pageIndex * pageSize;
			listed = from >= total ? new ArrayList<BatchJobDTO>()
			        : new ArrayList<>(listed.subList((int) from, (int) Math.min(total, from + pageSize)));
		}
		result.setData(listed);
		// after the paging setters, which ask for the count to be loaded again
		result.setTotalRecordCount(total);
		return result;
	}

	/**
	 * 2.1.2's search: with privilege filters it keeps the jobs at their locations, without them
	 * every job that matches the other filters, whatever its location, with its owners and names.
	 */
	protected Result<BatchJobDTO> findBatchJobs212(BatchJobSearchFilter filter,
	        HashSet<RecordPrivilegeFilter> recordPrivilegeFilters) {
		return super.findBatchJobs(filter, recordPrivilegeFilters);
	}
}
