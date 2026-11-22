package org.openmrs.module.stockmanagement.api.dao;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;
import org.openmrs.module.stockmanagement.api.dao.LocalizedStockManagementDao.NameCandidate;
import org.openmrs.module.stockmanagement.api.dto.ConceptNameDTO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 2.1.2 named a unit by the first fully specified name of any locale, so a French session read
 * « (Capsule) » in the dispense batch list (UVL-EMR#529).
 */
public class LocalizedStockManagementDaoTest {

	private static final Integer CAPSULE = 191;

	private static final Integer BOTTLE = 132;

	private static final Integer ENGLISH_ONLY = 2000;

	/** The names of the UAT unit concepts, in concept_name_id order: English first. */
	private static final List<NameCandidate> NAMES = Arrays.asList(
	    name(CAPSULE, "Capsule", "en", true, true),
	    name(CAPSULE, "Gélule", "fr", true, true),
	    name(CAPSULE, "Cápsula", "es", true, false),
	    name(BOTTLE, "Bottle", "en", true, true),
	    name(BOTTLE, "Frasco", "es", true, false),
	    name(BOTTLE, "Flacon", "fr", true, true),
	    name(ENGLISH_ONLY, "Sachet", "en", true, true));

	@Test
	public void getConceptNamesByConceptIds_shouldNameEachConceptOnceInTheUserLocale() {
		List<ConceptNameDTO> names = dao(Locale.FRENCH).getConceptNamesByConceptIds(Arrays.asList(CAPSULE, BOTTLE));

		assertEquals(2, names.size());
		assertEquals(CAPSULE, names.get(0).getConceptId());
		assertEquals("Gélule", names.get(0).getName());
		assertEquals(BOTTLE, names.get(1).getConceptId());
		assertEquals("Flacon", names.get(1).getName());
	}

	@Test
	public void getConceptNames_shouldNameEachConceptInTheUserLocale() {
		Map<Integer, String> names = dao(Locale.FRENCH).getConceptNames(Arrays.asList(CAPSULE, BOTTLE, ENGLISH_ONLY));

		assertEquals("Gélule", names.get(CAPSULE));
		assertEquals("Flacon", names.get(BOTTLE));
		assertEquals("Sachet", names.get(ENGLISH_ONLY));
	}

	@Test
	public void getConceptNamesByConceptIds_shouldKeepEnglishForAnEnglishSession() {
		List<ConceptNameDTO> names = dao(Locale.ENGLISH).getConceptNamesByConceptIds(Arrays.asList(CAPSULE, BOTTLE));

		assertEquals("Capsule", names.get(0).getName());
		assertEquals("Bottle", names.get(1).getName());
	}

	@Test
	public void getConceptNamesByConceptIds_shouldReturnNothingForNoIds() {
		assertTrue(dao(Locale.FRENCH).getConceptNamesByConceptIds(Collections.emptyList()).isEmpty());
		assertTrue(dao(Locale.FRENCH).getConceptNamesByConceptIds(null).isEmpty());
		assertTrue(dao(Locale.FRENCH).getConceptNames(null).isEmpty());
	}

	@Test
	public void pickName_shouldPreferTheLocalePreferredNameOverTheFullySpecifiedOne() {
		List<NameCandidate> candidates = Arrays.asList(name(CAPSULE, "Gélule (forme)", "fr", false, true),
		    name(CAPSULE, "Gélule", "fr", true, false));

		assertEquals("Gélule", LocalizedStockManagementDao.pickName(candidates, Locale.FRENCH).name);
	}

	@Test
	public void pickName_shouldUseTheLanguageWhenTheCountryDoesNotMatch() {
		assertEquals("Gélule", LocalizedStockManagementDao.pickName(NAMES.subList(0, 3), Locale.FRANCE).name);
	}

	@Test
	public void pickName_shouldFallBackToTheFirstFullySpecifiedName() {
		assertEquals("Capsule", LocalizedStockManagementDao.pickName(NAMES.subList(0, 3), Locale.GERMAN).name);
	}

	@Test
	public void pickName_shouldUseALocalePreferredNameThatIsNotFullySpecified() {
		assertEquals("Cápsula", LocalizedStockManagementDao.pickName(NAMES.subList(0, 3), new Locale("es")).name);
	}

	private static LocalizedStockManagementDao dao(final Locale locale) {
		return new LocalizedStockManagementDao() {

			@Override
			protected Locale getLocale() {
				return locale;
			}

			@Override
			protected List<NameCandidate> findNameCandidates(List<Integer> ids) {
				return NAMES.stream().filter(n -> ids.contains(n.conceptId)).collect(java.util.stream.Collectors.toList());
			}
		};
	}

	private static NameCandidate name(Integer conceptId, String name, String locale, boolean preferred,
	        boolean fullySpecified) {
		return new NameCandidate(conceptId, name, new Locale(locale), preferred, fullySpecified);
	}
}
