package com.jtech.service.rql.models;

import org.json.JSONArray;
import org.json.JSONObject;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public class QueryParamWrapper {
	private final JSONObject filter;
	private final JSONArray filterOr;
	private final JSONArray range;
	private final JSONArray sort;
}