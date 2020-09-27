package com.jtech.service.rql.services.ql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.IdentifiableType;
import javax.persistence.metamodel.Metamodel;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.jtech.service.rql.utils.CustomQLConstants;

@Service
public class CustomSpecifications<T> {
	@PersistenceContext
	private EntityManager entityManager;

	public Specification<T> customSpecificationBuilder(Map<String, Object> map) {
		return (root, query, builder) -> {
			query.distinct(true);
			List<Predicate> predicates = handleMap(builder, root, null, query, map, new ArrayList<>());
			return builder.and(predicates.toArray(new Predicate[predicates.size()]));
		};
	}

	public Predicate customSpecificationBuilder(CriteriaBuilder builder, CriteriaQuery<?> query, Root<?> root, Map<String, Object> map) {
		query.distinct(true);
		List<Predicate> predicates = handleMap(builder, root, null, query, map, new ArrayList<>());
		return builder.and(predicates.toArray(new Predicate[predicates.size()]));

	}

	public Predicate customSpecificationBuilder(CriteriaBuilder builder, CriteriaQuery<?> query, Root<?> root, List<Map<String, Object>> list) {
		query.distinct(true);
		List<Predicate> orPredicates = new ArrayList<>();
		for (Map<String, Object> map : list) {
			List<Predicate> predicates = handleMap(builder, root, null, query, map, new ArrayList<>());
			Predicate orPred = builder.and(predicates.toArray(new Predicate[predicates.size()]));
			orPredicates.add(orPred);
		}
		return builder.or(orPredicates.toArray(new Predicate[orPredicates.size()]));
	}

	private List<Predicate> handleMap(CriteriaBuilder builder, Root<?> root, Join<?, ?> join, CriteriaQuery<?> query, Map<?, ?> map, List<String> includeOnlyFields) {
		if (join != null) {
			root = query.from(getJavaTypeOfClassContainingAttribute(root, join.getAttribute().getName()));
		}
		List<Predicate> predicates = new ArrayList<>();
		Predicate pred;
		if (map.containsKey("q") && map.get("q") instanceof String) {

			predicates.add(searchInAllAttributesPredicate(builder, root, (String) map.get("q"), includeOnlyFields));
			map.remove("q");
		}
		Set<?> attributes = root.getModel().getAttributes();
		for (Map.Entry<?, ?> e : map.entrySet()) {
			String key = (String) e.getKey();
			Object val = e.getValue();
			String cleanKey = cleanUpKey(key);

			Attribute<?, ?> a = root.getModel().getAttribute(cleanKey);
			if (attributes.contains(a)) {
				pred = handleAllCases(builder, root, join, query, a, key, val);
				predicates.add(pred);
			}
		}
		return predicates;
	}

	private Predicate handleAllCases(CriteriaBuilder builder, Root<?> root, Join<?, ?> join, CriteriaQuery<?> query, Attribute<?, ?> a, String key, Object val) {
		boolean isValueCollection = val instanceof Collection;
		boolean isValueMap = val instanceof Map;
		String cleanKey = cleanUpKey(key);
		boolean isKeyClean = cleanKey.equals(key);
		boolean isNegation = key.endsWith("Not");
		boolean isGt = key.endsWith("Gt");
		boolean isGte = key.endsWith("Gte");
		boolean isLt = key.endsWith("Lt");
		boolean isLte = key.endsWith("Lte");
		boolean isConjunction = key.endsWith("And");
		boolean isAssociation = a.isAssociation();

		if (isValueMap) {
			val = convertMapContainingPrimaryIdToValue(val, a, root);
		}
		if (val instanceof Map && isAssociation) {
			List<?> predicates = handleMap(builder, root, addJoinIfNotExists(root, a, isConjunction, isValueCollection), query, ((Map<?, ?>) val), Arrays.asList());
			Predicate[] predicatesArray = predicates.toArray(new Predicate[predicates.size()]);
			return builder.and(predicatesArray);
		}

		if (isKeyClean) {
			return handleCleanKeyCase(builder, root, join, query, cleanKey, a, val);
		} else if (isNegation) {
			return builder.not(handleCleanKeyCase(builder, root, join, query, cleanKey, a, val));
		} else if (isConjunction) {
			if (isValueCollection) {
				return handleCollection(builder, root, join, query, a, cleanKey, (Collection<?>) val, true);
			}
		} else if (isLte) {
			return createLtePredicate(builder, root, a, val);
		} else if (isGte) {
			return createGtePredicate(builder, root, a, val);
		} else if (isLt) {
			return createLtPredicate(builder, root, a, val);
		} else if (isGt) {
			return createGtPredicate(builder, root, a, val);
		}
		return builder.conjunction();
	}

	private Predicate handleCollection(CriteriaBuilder builder, Root<?> root, Join<?, ?> join, CriteriaQuery<?> query, Attribute<?, ?> a, String key, Collection<?> values, boolean conjunction) {
		List<Predicate> predicates = new ArrayList<>();
		for (Object val : values) {
			Predicate pred = handleAllCases(builder, root, join, query, a, key, val);
			predicates.add(pred);
		}
		Predicate[] predicatesArray = predicates.toArray(new Predicate[predicates.size()]);
		return (conjunction) ? builder.and(predicatesArray) : builder.or(predicatesArray);
	}

	private Predicate handleCleanKeyCase(CriteriaBuilder builder, Root<?> root, Join<?, ?> join, CriteriaQuery<?> query, String key, Attribute<?, ?> a, Object val) {
		boolean isValueCollection = val instanceof Collection;
		boolean isValTextSearch = (val instanceof String) && ((String) val).contains("%");
		if (isValueCollection) {
			return handleCollection(builder, root, join, query, a, key, (Collection<?>) val, false);
		} else if (isValTextSearch) {
			return createLikePredicate(builder, root, join, a, (String) val);
		} else if (a.isCollection() && !a.isAssociation()) {
			return createEqualityPredicate(builder, root, addJoinIfNotExists(root, a, false, isValueCollection), a, val);
		} else {
			return createEqualityPredicate(builder, root, join, a, val);
		}
	}

	public String getIdAttributeName(Class<?> clazz) {
		Metamodel m = entityManager.getMetamodel();
		IdentifiableType<?> of = (IdentifiableType<?>) m.managedType(clazz);
		return of.getId(of.getIdType().getJavaType()).getName();
	}

	private Class<?> getIdAttributeJavaType(Class<?> clazz) {
		Metamodel m = entityManager.getMetamodel();
		IdentifiableType<?> of = (IdentifiableType<?>) m.managedType(clazz);
		return of.getId(of.getIdType().getJavaType()).getJavaType();
	}

	private String cleanUpKey(String key) {
		List<String> postfixes = Arrays.asList("Gte", "Gt", "Lte", "Lt", "Not", "And");
		for (String postfix : postfixes) {
			if (key.endsWith(postfix)) {
				return key.substring(0, key.length() - postfix.length());
			}
		}
		return key;
	}

	private Predicate searchInAllAttributesPredicate(CriteriaBuilder builder, Root<?> root, String text, List<String> includeOnlyFields) {
		if (!text.contains("%")) {
			text = "%" + text + "%";
		}
		final String finalText = text;
		Set<?> set = root.getModel().getAttributes();
		Set<Attribute<?, ?>> attributes = this.getAttributeSetFromSet(set);
		List<Predicate> orPredicates = new ArrayList<>();
		for (Attribute<?, ?> a : attributes) {
			boolean javaTypeIsString = a.getJavaType().equals(String.class);
			boolean shouldSearch = includeOnlyFields.isEmpty() || includeOnlyFields.contains(a.getName());
			if (javaTypeIsString && shouldSearch) {
				Predicate orPred = builder.like(root.get(a.getName()), finalText);
				orPredicates.add(orPred);
			}

		}
		return builder.or(orPredicates.toArray(new Predicate[orPredicates.size()]));

	}

	private Predicate createEqualityPredicate(CriteriaBuilder builder, Root<?> root, Join<?, ?> join, Attribute<?, ?> a, Object val) {
		if (isNull(a, val)) {
			if (a.isAssociation() && a.isCollection()) {
				return builder.isEmpty(root.get(a.getName()));
			} else if (isPrimitiveOrString(a)) {
				return builder.isNull(root.get(a.getName()));
			} else {
				return root.get(a.getName()).isNull();
			}
		} else if (join == null) {
			if (isPrimitiveOrString(a)) {
				return builder.equal(root.get(a.getName()), val);
			} else if (isUUID(a)) {
				return builder.equal(root.get(a.getName()), UUID.fromString(val.toString()));
			} else if (a.isAssociation()) {
				if (isPrimaryKeyOfAttributeUUID(a, root)) {
					return prepareJoinAssociatedPredicate(builder, root, a, UUID.fromString(val.toString()));
				} else {
					return prepareJoinAssociatedPredicate(builder, root, a, val);
				}
			}
		} else {
			if (isPrimitiveOrString(a)) {
				return builder.equal(join.get(a.getName()), val);
			} else if (a.isAssociation()) {
				return builder.equal(join.get(a.getName()), val);
			} else if (a.isCollection()) {
				return builder.equal(join, val);
			}
		}
		throw new IllegalArgumentException(CustomQLConstants.EQUALITY_PREDICATE_SUPPORTED_MESSAGE);
	}

	private Predicate createLikePredicate(CriteriaBuilder builder, Root<?> root, Join<?, ?> join, Attribute<?, ?> a, String val) {
		if (join == null) {
			return builder.like(root.get(a.getName()), val);
		} else {
			return builder.like(join.get(a.getName()), val);
		}
	}

	private Predicate createGtPredicate(CriteriaBuilder builder, Root<?> root, Attribute<?, ?> a, Object val) {
		if (val instanceof String) {
			return builder.greaterThan(builder.lower(root.get(a.getName())), ((String) val).toLowerCase());
		} else if (val instanceof Integer) {
			return builder.greaterThan(root.get(a.getName()), (Integer) val);
		}
		throw new IllegalArgumentException(CustomQLConstants.VALUE_TYPE_NOT_SUPPORTED);
	}

	private Predicate createGtePredicate(CriteriaBuilder builder, Root<?> root, Attribute<?, ?> a, Object val) {
		if (val instanceof String) {
			return builder.greaterThanOrEqualTo(builder.lower(root.get(a.getName())), ((String) val).toLowerCase());
		} else if (val instanceof Integer) {
			return builder.greaterThanOrEqualTo(root.get(a.getName()), (Integer) val);
		}
		throw new IllegalArgumentException(CustomQLConstants.VALUE_TYPE_NOT_SUPPORTED);
	}

	private Predicate createLtPredicate(CriteriaBuilder builder, Root<?> root, Attribute<?, ?> a, Object val) {
		if (val instanceof String) {
			return builder.lessThan(builder.lower(root.get(a.getName())), ((String) val).toLowerCase());
		} else if (val instanceof Integer) {
			return builder.lessThan(root.get(a.getName()), (Integer) val);
		}
		throw new IllegalArgumentException(CustomQLConstants.VALUE_TYPE_NOT_SUPPORTED);
	}

	private Predicate createLtePredicate(CriteriaBuilder builder, Root<?> root, Attribute<?, ?> a, Object val) {
		if (val instanceof String) {
			return builder.lessThanOrEqualTo(builder.lower(root.get(a.getName())), ((String) val).toLowerCase());
		} else if (val instanceof Integer) {
			return builder.lessThanOrEqualTo(root.get(a.getName()), (Integer) val);
		}
		throw new IllegalArgumentException(CustomQLConstants.VALUE_TYPE_NOT_SUPPORTED);
	}

	private Predicate prepareJoinAssociatedPredicate(CriteriaBuilder builder, Root<?> root, Attribute<?, ?> a, Object val) {
		Path<?> rootJoinGetName = addJoinIfNotExists(root, a, false, false);
		Class<?> referencedClass = rootJoinGetName.getJavaType();
		String referencedPrimaryKey = getIdAttributeName(referencedClass);
		return builder.equal(rootJoinGetName.get(referencedPrimaryKey), val);
	}

	private Join<?, ?> addJoinIfNotExists(Root<?> root, Attribute<?, ?> a, boolean isConjunction, boolean isValueCollection) {
		if (isConjunction && isValueCollection) {
			return root.join(a.getName());
		}
		Set<?> set = root.getJoins();
		Set<Join<?, ?>> joins = this.getJoinSetFromSet(set);
		Join<?, ?> toReturn = null;
		for (Join<?, ?> join : joins) {
			if (a.getName().equals(join.getAttribute().getName())) {
				toReturn = join;
				break;
			}
		}
		if (toReturn == null) {
			toReturn = root.join(a.getName());
		}
		return toReturn;
	}

	private Set<Join<?, ?>> getJoinSetFromSet(Set<?> set) {
		Set<Join<?, ?>> result = new HashSet<>();
		for (Object o : set) {
			if (Objects.nonNull(o) && o instanceof Join<?, ?>) {
				result.add((Join<?, ?>) o);
			}
		}
		return result;
	}

	private Set<Attribute<?, ?>> getAttributeSetFromSet(Set<?> set) {
		Set<Attribute<?, ?>> result = new HashSet<>();
		for (Object o : set) {
			if (Objects.nonNull(o) && o instanceof Join<?, ?>) {
				result.add((Attribute<?, ?>) o);
			}
		}
		return result;
	}

	private Class<?> getJavaTypeOfClassContainingAttribute(Root<?> root, String attributeName) {
		Attribute<?, ?> a = root.getModel().getAttribute(attributeName);
		if (a.isAssociation()) {
			return addJoinIfNotExists(root, a, false, false).getJavaType();
		}
		return null;
	}

	private boolean isPrimaryKeyOfAttributeUUID(Attribute<?, ?> a, Root<?> root) {
		Class<?> javaTypeOfClassContainingAttribute = getJavaTypeOfClassContainingAttribute(root, a.getName());
		return getIdAttributeJavaType(javaTypeOfClassContainingAttribute).equals(UUID.class);
	}

	private Object convertMapContainingPrimaryIdToValue(Object val, Attribute<?, ?> a, Root<?> root) {
		Class<?> javaTypeOfAttribute = getJavaTypeOfClassContainingAttribute(root, a.getName());
		String primaryKeyName = getIdAttributeName(javaTypeOfAttribute);
		if (val instanceof Map && ((Map<?, ?>) val).keySet().size() == 1) {
			Map<?, ?> map = ((Map<?, ?>) val);
			for (Object key : map.keySet()) {
				if (key.equals(primaryKeyName)) {
					return map.get(primaryKeyName);
				}
			}
		}
		return val;
	}

	private boolean isUUID(Attribute<?, ?> attribute) {
		return attribute.getJavaType().equals(UUID.class);
	}

	private boolean isPrimitiveOrString(Attribute<?, ?> attribute) {
		Class<?> clz = attribute.getJavaType();
		return ClassUtils.isPrimitiveOrWrapper(clz) || clz.equals(String.class);
	}

	private boolean isNull(Attribute<?, ?> attribute, Object val) {
		if (isPrimitiveOrString(attribute)) {
			if (attribute.getJavaType().equals(String.class)) {
				String valObj = (String) val;
				return StringUtils.isBlank(valObj) || valObj.equalsIgnoreCase("null");
			} else {
				return val == null;
			}
		} else {
			return val == null;
		}
	}
}