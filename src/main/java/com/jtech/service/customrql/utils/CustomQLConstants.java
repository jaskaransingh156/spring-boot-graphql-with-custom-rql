package com.jtech.service.customrql.utils;

public class CustomQLConstants {

    private CustomQLConstants() {
    }

    public static final String EQUALITY_PREDICATE_SUPPORTED_MESSAGE = "equality/inequality is currently supported on primitives";
    public static final String VALUE_TYPE_NOT_SUPPORTED = "Provided value type not supported";
    public static final String SORT_LENGTH_MESSAGE = "sort should have even length given as array e.g ['name', 'ASC', 'birthDate', 'DESC']";
}