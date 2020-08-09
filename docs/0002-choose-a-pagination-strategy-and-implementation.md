---
id: 0002-choose-a-pagination-strategy-and-implementation
title: 2 - Choose a Pagination Strategy and Implementation
---
Date: 2019-12-31

## Status

Accepted

## Context

The SpatioTemporal Asset Catalog (STAC) API specification allows for the use of `next` links in the responses for the the
`/search` and `/collections/<id>/items` endpoints. The specific implementation for creating a token and scheme for constructing
the parameters to get the next page of results is left open to different implementations.

According to the OGC API - Features specification for links in responses the `next` relation signifies:
> the link’s context is a part of a series, and that the next in the series is the link target 

Since the parameters of the `href` itself is not specified to follow a certain pattern or any additional requirements 
implementation itself is left to `franklin`. There are a few common options that have been [documented](https://www.moesif.com/blog/technical/api-design/REST-API-Design-Filtering-Sorting-and-Pagination/) [by](https://nordicapis.com/everything-you-need-to-know-about-api-pagination/) [a](https://phauer.com/2015/restful-api-design-best-practices/) [number](https://phauer.com/2018/web-api-pagination-timestamp-id-continuation-token/) [of](https://developers.facebook.com/docs/graph-api/using-graph-api/#paging) 
[existing](https://developer.github.com/v3/#pagination) [resources](https://developer.spotify.com/documentation/web-api/reference/object-model/#paging-object).

### limit & offset

Using `offset` and `limit` parameters is a common patten for paginating results. With this approach the `limit` 
parameter specifies the number of results in a response and the `offset` parameter specifies which particular set of 
results out of the total to return. This maps closely to how `limit` and `offset` work in `SQL` already.

#### Benefits

**Easy to implement**: maps closely to `SQL` and therefore easy to implement

**Stateless**: requires no additional context

#### Disadvantages

**Performance**: can be slow for large datasets

**Inconsistent**: depending on sort ordering and the inclusion/deletion of items the same results can be returned multiple times

### keyset/cursor/continuation token

The `keyset`, `cursor`, or `continuation` token approach uses an opaque-*ish* token to control the page of results 
returned and can also be combined with a `limit` parameter to control the number of results. Often the token used will 
represent a unique identifier for the last result of the current page returned to the client. This token is then used in
subsequent requests use the token in a query parameter (e.g. `/?next=<token>`).

#### Benefits

**Performance**: on large datasets with indices performs better than scanning previous results

**Consistent Ordering**: even when data is added/removed results are stable

**Can page forward and backwards**: tokens can be used to page before and after search results

**Implementation logic hidden from users**: while there may be an interpretation of a continuation token, it is not
guaranteed to be consistent or long-lived, which frees up back-end optimizations and changes without breaking the API

#### Disadvantages
**Implementation more complex**: needs to manage sort ordering to ensure that the continuation token equality check is
consistent even if data is added, removed, or sorted by additional fields

**Tokens become invalid**: continuation tokens can become invalid, users should not rely on them for long periods of 
time

## Decision

We will use **continuation** tokens for managing pagination. Keeping the implementation flexible/hidden
from end-users so that it can be optimized or changed later without breaking functionality for existing users outweighs
disadvantages around increased complexity for implementation. Additionally, since `franklin` APIs are expected to hold 
large amounts of data (tens of millions of rows) it is important that the pagination approach scales accordingly.
Lastly, since eventually `franklin` may move beyond relying solely on `postgresl` for managing state it may actually be
better that we do not use `offset/limit` which relies heavily on `SQL`. 

## Consequences

An upcoming release for the [STAC specification](https://github.com/radiantearth/stac-spec/blob/dev/CHANGELOG.md#changed) 
changes how pagination will be communicated in the API and simplifies implementation. In order to implement this we will 
need to update API responses for `/search` and `/collections/<id>/items/` to include a `next` link for
paginating forward. Initial implementation should `base64` encode the primary key for the last result in the current
page.

One thing that is unclear is how we should handle primary keys/identifiers when implementing `continuation` pagination.
This approach to pagination relies on sorting by an identifier that signals the last value in the current page. If that 
value is a primary key then then including the PK in the `sort by` for `SQL` queries with a limit gives the next set of 
results. For a `SERIAL` column in `postgresql` this is performant with an index. However, we use client supplied 
identifiers right now for IDs since they are likely meaningful to the client and don't create our own PK. In 
implementing a `continuation` token based approach we should keep the user supplied identifier, but also add a column 
for a real PK that is a `SERIAL` column.

The implementation should follow the approach laid out [here](https://phauer.com/2018/web-api-pagination-timestamp-id-continuation-token/) 
every query ends up being something like:

```SQL
SELECT * FROM table
WHERE 
  id > {continuation-id} 
ORDER BY <any-user-supplied-sort-options>, id asc
LIMIT <page-size>
```
