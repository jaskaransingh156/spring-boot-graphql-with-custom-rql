package com.jtech.service.customrql.services.graphql;

import java.util.List;

import javax.inject.Inject;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import com.jtech.service.customrql.domains.User;
import com.jtech.service.customrql.models.QueryParamWrapper;
import com.jtech.service.customrql.services.ql.FilterService;
import com.jtech.service.customrql.utils.Utils;

@Component
public class GraphQLQueryService implements GraphQLQueryResolver {
    @Inject
    private FilterService<User, Integer> userFilterService;

    public List<User> getUserList(String filter, String range, String sort) {
        QueryParamWrapper queryParamWrapper = Utils.extractQueryParams(filter, range, sort);
        Page<User> pages = userFilterService.filterBy(queryParamWrapper, User.class);
        return pages.getContent();
    }

    public long getUserCount(String filter) {
        QueryParamWrapper queryParamWrapper = Utils.extractQueryParams(filter, null, null);
        return userFilterService.countBy(queryParamWrapper);
    }
}