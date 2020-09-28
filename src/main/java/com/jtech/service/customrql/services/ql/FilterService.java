package com.jtech.service.customrql.services.ql;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.jtech.service.customrql.models.QueryParamWrapper;
import com.jtech.service.customrql.repositories.BaseRepository;
import com.jtech.service.customrql.utils.CustomQLConstants;
import com.jtech.service.customrql.utils.Utils;

@Service
public class FilterService<T, I extends Serializable> {
	private CustomSpecifications<T> specifications;
	private BaseRepository<T, I> baseRepository;

	@Inject
	public FilterService(CustomSpecifications<T> specifications, BaseRepository<T, I> baseRepository) {
		this.specifications = specifications;
		this.baseRepository = baseRepository;
	}

	public long countBy(QueryParamWrapper queryParamWrapper) {
		JSONObject filter = queryParamWrapper.getFilter();
		JSONArray filterOr = queryParamWrapper.getFilterOr();
		if (filter != null && filter.length() > 0) {
			HashMap<String, Object> map = (HashMap<String, Object>) filter.toMap();
			return baseRepository.count(specifications.customSpecificationBuilder(map));
		} else if (filterOr != null && filterOr.length() > 0) {
			return baseRepository.count((Specification<T>) (root, query, builder) -> {
				List<Map<String, Object>> list = Utils.toMapList(filterOr);
				return specifications.customSpecificationBuilder(builder, query, root, list);
			});
		} else {
			return baseRepository.count();
		}
	}

	public Page<T> filterBy(QueryParamWrapper queryParamWrapper, Class<T> clz) {
		String primaryKeyName = specifications.getIdAttributeName(clz);
		return filterByHelper(queryParamWrapper, primaryKeyName);
	}

	private List<Sort.Order> sortHelper(JSONArray sort, String primaryKeyName) {
		List<Sort.Order> sortOrders = new ArrayList<>();
		if (sort.length() % 2 != 0) {
			throw new IllegalArgumentException(CustomQLConstants.SORT_LENGTH_MESSAGE);
		}
		for (int i = 0; i < sort.length(); i = i + 2) {
			String sortBy;
			sortBy = (String) sort.get(i);
			sortOrders.add(new Sort.Order(Sort.Direction.valueOf((String) sort.get(i + 1)), sortBy));
		}
		if (sortOrders.isEmpty()) {
			sortOrders.add(new Sort.Order(Sort.Direction.ASC, primaryKeyName));
		}
		return sortOrders;
	}

	private Page<T> filterByHelper(QueryParamWrapper queryParamWrapper, String primaryKeyName) {
		Sort sortObj;
		JSONObject filter = queryParamWrapper.getFilter();
		JSONArray filterOr = queryParamWrapper.getFilterOr();
		JSONArray range = queryParamWrapper.getRange();
		JSONArray sort = queryParamWrapper.getSort();
		int page = 0;
		int size = Integer.MAX_VALUE;
		if (range.length() == 2) {
			page = (Integer) range.get(0);
			size = (Integer) range.get(1);
		}
		sortObj = Sort.by(sortHelper(sort, primaryKeyName));
		Page<T> result;
		if (filter != null && filter.length() > 0) {
			result = baseRepository.findAll((Specification<T>) (root, query, builder) -> {
				HashMap<String, Object> map = (HashMap<String, Object>) filter.toMap();
				return specifications.customSpecificationBuilder(builder, query, root, map);
			}, PageRequest.of(page, size, sortObj));
		} else if (filterOr != null && filterOr.length() > 0) {
			result = baseRepository.findAll((Specification<T>) (root, query, builder) -> {
				List<Map<String, Object>> list = Utils.toMapList(filterOr);
				return specifications.customSpecificationBuilder(builder, query, root, list);
			}, PageRequest.of(page, size, sortObj));
		} else {
			result = baseRepository.findAll(PageRequest.of(page, size, sortObj));
		}
		return result;
	}
}