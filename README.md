# spring-boot-graphql-with-custom-rsql

## Context

As a Backend Engineer working on Microservices and creating REST APIs, I always felt the need of having some way so that I don't need to create multiple REST APIs for same resource. There are mainly two reasons due to which the requirement of having multiple APIs for same resource comes:

- First reason is amount of data required is different in different scenarios. For eg: consider a User resource having say 20 attributes. Imagine two scenarios - first where all 20 attributes are required by client and second where only 2 of those 20 attributes are required. We can live with sending extra data in second scenario but it would unnecessarily impact the performance due to transfer of unneeded data over the wire.
- Second reason is say earlier, we had given filtering & ordering functionality with a certain combination of certain fields and now we require to do filtering in some other particular way say some new combination of AND and OR of some fields.

Thankfully, there are solutions already available for both of the above problems. People, well versed with [GraphQL](https://github.com/graphql), would be knowing that first problem is solved by it i.e. client of REST API can tell us the fields that it require in the request itself and same API will output different set of fields depending upon input. For second problem too, there are solutions available like [RSQL](https://github.com/jirutka/rsql-parser) and [spring-boot-rest-api-helpers](https://github.com/Nooul/spring-boot-rest-api-helpers).

Although the solutions do exist for both the problems but if we go for first solution, we have to compromise on the second problem and vice versa. There is no combined solution available. My Aim was to somehow combine the functionality of both into one so that once I define a REST API on a resource, I don't need to make changes in it again due to new requirements coming up in any later stage of project.   

## Usage

Just we like we normally define schema and query for graphql, we will define them. The only difference would be we would be taking 3 strings of filter, range and sort as input in Query.

```
type Query {
    userList(filter: String, range: String, sort: String): [User!]!
    userCount(filter: String): Int!
}

type User {
    id: ID!
    name: String!
    userAddressList: [UserAddress!]!
}

type UserAddress {
    id: ID!
    userId: Int!
    address: String!
    user: User!
}
```

Now, we will define the functions, corresponding to the GraphQL query, in the following way:


```java
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
```

So, some sample format of GraphQL queries would be like following:

```
{
  userList(filter: "{id: 2}", range: "[0, 100]", sort: "[name, DESC]") { # Fetch user with id = 2
    name
    userAddressList {
      id
      address
    }
  }
}

{
  userList(filter: "[{id: 2}, {name: 'Jaskaran Singh'}]") { # Fetch user with id = 2 or name = 'Jaskaran Singh'
    name
    userAddressList {
      id
      address
    }
  }
}

{
  userList(filter: "[{id: 2}, {name: 'Jaskaran Singh'}]") { # Fetch users with id = 2 or name = 'Jaskaran Singh'
    name
    userAddressList {
      id
      address
    }
  }
}

{
  userList(filter: "{userAddressList: {address: 'Address 1'}}") { # Fetch users with address = 'Address 1'
    name
    userAddressList {
      id
      address
    }
  }
}
```

Please note in the last example that we are able to query on the sub-resource too.

## Credits

- [GraphQL](https://github.com/graphql)
- [spring-boot-rest-api-helpers](https://github.com/Nooul/spring-boot-rest-api-helpers)