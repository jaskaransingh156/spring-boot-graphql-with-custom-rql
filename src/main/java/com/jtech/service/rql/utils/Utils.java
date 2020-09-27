package com.jtech.service.rql.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtech.service.rql.models.QueryParamWrapper;

public class Utils {
	private static final Logger logger = LoggerFactory.getLogger(Utils.class);

	private Utils() {
	}

	public static List<Map<String, Object>> toMapList(JSONArray jsonArray) {
		List<Map<String, Object>> list = new ArrayList<>();
		if (Objects.nonNull(jsonArray)) {
			for (int i = 0; i < jsonArray.length(); i++) {
				Map<String, Object> map = jsonArray.getJSONObject(i).toMap();
				list.add(map);
			}
		}
		return list;
	}

	public static QueryParamWrapper extractQueryParams(String filterStr, String rangeStr, String sortStr) {
		Object filterJsonOrArray;
		if (StringUtils.isBlank(filterStr)) {
			filterStr = "{}";
		}
		filterStr = filterStr.replaceAll("%(?![0-9a-fA-F]{2})", "%25");
		filterStr = filterStr.replaceAll("\\+", "%2B");
		try {
			filterStr = URLDecoder.decode(filterStr.replace("+", "%2B"), "UTF-8").replace("%2B", "+");
		} catch (UnsupportedEncodingException e) {
			logger.error("[extract] Exception", e);
		}
		filterJsonOrArray = new JSONTokener(filterStr).nextValue();
		JSONObject filter = null;
		JSONArray filterOr = null;
		if (filterJsonOrArray instanceof JSONObject) {
			filter = new JSONObject(filterStr);
		} else if (filterJsonOrArray instanceof JSONArray) {
			filterOr = new JSONArray(filterStr);
		}
		JSONArray range;
		if (StringUtils.isBlank(rangeStr)) {
			rangeStr = "[]";
		}
		range = new JSONArray(rangeStr);
		JSONArray sort;
		if (StringUtils.isBlank(sortStr)) {
			sortStr = "[]";
		}
		sort = new JSONArray(sortStr);
		return new QueryParamWrapper(filter, filterOr, range, sort);
	}
}