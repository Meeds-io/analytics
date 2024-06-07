# Addon: meeds-analytics

An addon that provide tools to display platform usage statistics using charts.

## Configuration options

| VARIABLE               | MANDATORY | DEFAULT VALUE | DESCRIPTION                                                                               |
|------------------------|-----------|---------------|-------------------------------------------------------------------------------------------|
| analytics.admin.permission        | NO        | *:/platform/analytics | Group of users that can modify Charts application settings. All other users, *even members of /platform/administrators* will not be able to modify charts settings |
| analytics.viewall.permission        | NO        | *:/platform/administrators | Group of users that can consult all data in Graphs. |
| analytics.view.permission        | NO        | *:/platform/users | Group of users that can consult their personal and their spaces Graphs. |
| analytics.aggregation.terms.doc_size        | NO        | 200 | Limit of number of resturned documents in aggregations result of type 'terms' (not used for aggregations of type : sum, avg, date_histogram, histogram and cardinality) |
| analytics.es.index.server.url | NO        | Same as used for Meeds | Elasticsearch server URL used for indexing and searching analytics content |
| analytics.es.index.server.username | NO        | Same as used for Meeds | Elasticsearch server username used for indexing and searching analytics content |
| analytics.es.index.server.password | NO        | Same as used for Meeds | Elasticsearch server password used for indexing and searching analytics content |
| analytics.es.index.writePeriod | YES | 7 | Number of days to keep an Analytics index available for Read/Write mode. Once the period is passed, another index is created with a different suffix. |
| analytics.queue.MaxNodes | NO        | 2000             | Number of maximum entries in in-memory cached Analytics Queue that is processed each 10 seconds |
| analytics.queue.TimeToLive | NO        | -1             | lifetime of entries in Analytics Queue. Default: infinite. |
