package org.openmrs.module.stockmanagement.api.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.openmrs.module.stockmanagement.api.dto.BatchJobDTO;
import org.openmrs.module.stockmanagement.api.dto.BatchJobSearchFilter;
import org.openmrs.module.stockmanagement.api.dto.RecordPrivilegeFilter;
import org.openmrs.module.stockmanagement.api.dto.Result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 2.1.2 listed a batch job only if its location was in the user's reports scope, and a Report job
 * has no location, so a pharmacist never saw the reports they asked for (UVL-EMR#862).
 */
public class LocalizedStockManagementDaoBatchJobTest {

	private static final Integer IN_SCOPE = 22;

	private static final Integer OUT_OF_SCOPE = 8;

	@Test
	public void findBatchJobs_shouldListAJobWithoutALocationForAUserWithAReportsScope() {
		Result<BatchJobDTO> result = dao(job("a", null)).findBatchJobs(new BatchJobSearchFilter(), scope(IN_SCOPE));

		assertEquals(Arrays.asList("a"), uuids(result));
		assertEquals(Long.valueOf(1), result.getTotalRecordCount());
	}

	@Test
	public void findBatchJobs_shouldListAJobAtALocationOfTheScope() {
		assertEquals(Arrays.asList("a"),
		    uuids(dao(job("a", IN_SCOPE)).findBatchJobs(new BatchJobSearchFilter(), scope(IN_SCOPE))));
	}

	@Test
	public void findBatchJobs_shouldNotListAJobAtALocationOutsideTheScope() {
		Result<BatchJobDTO> result = dao(job("a", OUT_OF_SCOPE), job("b", null)).findBatchJobs(new BatchJobSearchFilter(),
		    scope(IN_SCOPE));

		assertEquals(Arrays.asList("b"), uuids(result));
		assertEquals(Long.valueOf(1), result.getTotalRecordCount());
	}

	@Test
	public void findBatchJobs_shouldListNothingForAUserWithoutAReportsScope() {
		FakeDao dao = dao(job("a", null), job("b", IN_SCOPE));
		Result<BatchJobDTO> result = dao.findBatchJobs(new BatchJobSearchFilter(), new HashSet<RecordPrivilegeFilter>());

		assertEquals(0, dao.calls);
		assertTrue(result.getData().isEmpty());
		assertEquals(Long.valueOf(0), result.getTotalRecordCount());
	}

	@Test
	public void findBatchJobs_shouldLeaveTheSearchTo212WhenNoPrivilegeFiltersAreGiven() {
		FakeDao dao = dao(job("a", null), job("b", OUT_OF_SCOPE));

		Result<BatchJobDTO> result = dao.findBatchJobs(new BatchJobSearchFilter(), null);

		assertEquals(1, dao.calls);
		assertEquals(null, dao.filtersSeen);
		assertEquals(Arrays.asList("a", "b"), uuids(result));
	}

	@Test
	public void findBatchJobs_shouldLeaveTheSearchTo212WhenTheRequestNamesALocation() {
		FakeDao dao = dao(job("a", null));
		BatchJobSearchFilter filter = new BatchJobSearchFilter();
		filter.setLocationScopeIds(new ArrayList<>(Arrays.asList(IN_SCOPE)));
		HashSet<RecordPrivilegeFilter> scope = scope(IN_SCOPE);

		dao.findBatchJobs(filter, scope);

		assertEquals(1, dao.calls);
		assertEquals(scope, dao.filtersSeen);
	}

	@Test
	public void findBatchJobs_shouldPageTheJobsItKeeps() {
		FakeDao dao = dao(job("a", null), job("b", OUT_OF_SCOPE), job("c", IN_SCOPE), job("d", null));
		BatchJobSearchFilter filter = new BatchJobSearchFilter();
		filter.setLimit(2);
		filter.setStartIndex(1);

		Result<BatchJobDTO> second = dao.findBatchJobs(filter, scope(IN_SCOPE));

		assertEquals(Arrays.asList("d"), uuids(second));
		assertEquals(Long.valueOf(3), second.getTotalRecordCount());
		assertEquals(Integer.valueOf(2), filter.getLimit());
		assertEquals(Integer.valueOf(1), filter.getStartIndex());

		filter.setStartIndex(0);
		assertEquals(Arrays.asList("a", "c"), uuids(dao.findBatchJobs(filter, scope(IN_SCOPE))));
		filter.setStartIndex(5);
		assertTrue(dao.findBatchJobs(filter, scope(IN_SCOPE)).getData().isEmpty());
	}

	@Test
	public void findBatchJobs_shouldAskTheQueryForEveryPageAtOnce() {
		FakeDao dao = dao(job("a", null));
		BatchJobSearchFilter filter = new BatchJobSearchFilter();
		filter.setLimit(10);
		filter.setStartIndex(0);

		dao.findBatchJobs(filter, scope(IN_SCOPE));

		assertEquals(null, dao.limitSeen);
		assertEquals(null, dao.startSeen);
	}

	private static class FakeDao extends LocalizedStockManagementDao {

		final List<BatchJobDTO> jobs;

		int calls;

		HashSet<RecordPrivilegeFilter> filtersSeen = new HashSet<>();

		Integer limitSeen = -1;

		Integer startSeen = -1;

		FakeDao(List<BatchJobDTO> jobs) {
			this.jobs = jobs;
		}

		@Override
		protected Result<BatchJobDTO> findBatchJobs212(BatchJobSearchFilter filter,
		        HashSet<RecordPrivilegeFilter> recordPrivilegeFilters) {
			calls++;
			filtersSeen = recordPrivilegeFilters;
			limitSeen = filter.getLimit();
			startSeen = filter.getStartIndex();
			return new Result<>(new ArrayList<>(jobs), jobs.size());
		}
	}

	private static FakeDao dao(BatchJobDTO... jobs) {
		return new FakeDao(Arrays.asList(jobs));
	}

	private static BatchJobDTO job(String uuid, Integer locationId) {
		BatchJobDTO job = new BatchJobDTO();
		job.setUuid(uuid);
		job.setLocationScopeId(locationId);
		return job;
	}

	private static HashSet<RecordPrivilegeFilter> scope(Integer... locationIds) {
		HashSet<RecordPrivilegeFilter> filters = new HashSet<>();
		for (Integer locationId : locationIds) {
			RecordPrivilegeFilter filter = new RecordPrivilegeFilter();
			filter.setLocationId(locationId);
			filters.add(filter);
		}
		return filters;
	}

	private static List<String> uuids(Result<BatchJobDTO> result) {
		return result.getData().stream().map(BatchJobDTO::getUuid).collect(Collectors.toList());
	}
}
