# Addon: meeds-analytics

An addon that provide tools to display platform usage statistics using charts.

# Prerequisites

The addon requires Meeds server with Elasticsearch.

## Configuration options

You can can change some options of the addon using properties that you can add into [exo.properties](https://docs.exoplatform.org/en/latest/Configuration.html#configuration-configurationoverview) file

| VARIABLE               | MANDATORY | DEFAULT VALUE | DESCRIPTION                                                                               |
|------------------------|-----------|---------------|-------------------------------------------------------------------------------------------|
| exo.analytics.admin.permission        | NO        | *:/platform/analytics | Group of users that can modify Charts application settings. All other users, *even members of /platform/administrators* will not be able to modify charts settings |
| exo.analytics.viewall.permission        | NO        | *:/platform/administrators | Group of users that can consult all data in Graphs. |
| exo.analytics.view.permission        | NO        | *:/platform/users | Group of users that can consult their personal and their spaces Graphs. |
| exo.analytics.aggregation.terms.doc_size        | NO        | 200 | Limit of number of resturned documents in aggregations result of type 'terms' (not used for aggregations of type : sum, avg, date_histogram, histogram and cardinality) |
| exo.addon.analytics.es.mapping.path | NO        | jar:/analytics-es-mapping.json             | Initial [ES mapping](https://github.com/Meeds-io/analytics/blob/master/analytics-services/src/main/resources/analytics-es-mapping.json) that will be used to generate daily [ES index](https://www.elastic.co/guide/en/elasticsearch/reference/5.6/mapping.html)   |
| exo.cache.analytics.queue.MaxNodes | NO        | 2000             | Number of maximum entries in in-memory cached Analytics Queue that is processed each 10 seconds |
| exo.cache.analytics.queue.TimeToLive | NO        | -1             | lifetime of entries in Analytics Queue. Default: infinite. |
| exo.es.analytics.index.server.url | NO        | Same as used [ES for eXo](https://docs.exoplatform.org/en/latest/Configuration.html#properties-of-the-elasticsearch-client) | Elasticsearch server URL used for indexing and searching analytics content |
| exo.es.analytics.index.server.username | NO        | Same as used [ES for eXo](https://docs.exoplatform.org/en/latest/Configuration.html#properties-of-the-elasticsearch-client) | Elasticsearch server username used for indexing and searching analytics content |
| exo.es.analytics.index.server.password | NO        | Same as used [ES for eXo](https://docs.exoplatform.org/en/latest/Configuration.html#properties-of-the-elasticsearch-client) | Elasticsearch server password used for indexing and searching analytics content |
| exo.es.analytics.index.per.day | YES | true | Whether create one index per day or not. You can switch this flag to true or false whener you want, it will not affect retrieved data result. In fact, the Analytics engine searches in all present indexes in server by including systematically a documents field filter that is added automatically to all statistics data : `isAnalytics = true` |
| exo.es.analytics.index.prefix | YES | analytics | Elasticsearch index names prefix. IT will be exactly equals to this value when `exo.es.analytics.index.per.day=false`, else the index names will be of pattern `${exo.es.analytics.index.prefix}_NUMBER_OF_DAYS` |

## How to build addon

1. install Apache maven 3.5+
1. Go to Home path of addon
2. Execute the following command :

```bash
mvn clean verify -Prun-its
```

## How to use it

Once the addon installed and eXo Platform started,
and after [logged-in on eXo](https://docs.exoplatform.org/en/latest/GettingStarted.html#welcome-to-exo-platform),
you can follow those steps:

1. Add a user into analytics administrators (Under Platform => Analytics) group [using group managment UI](https://docs.exoplatform.org/en/latest/Administration.html#managing-groups)
2. Go to application registry and [add Analytics portlet](https://docs.exoplatform.org/en/latest/Administration.html#managing-applications) in an application category
3. Go to home page and [add a new page](https://docs.exoplatform.org/en/latest/Administration.html#adding-a-new-page) in current site
4. You can add multiple instances of analytics portlet inside the page to show multiple statistics data
5. Save the page

You will see the analytics application added in your page and displaying by default a default chart including all statistics data.
You can change settings of each application to show you different charts type, title and statistics data.

By default, the user root is a member of `/platform/*analytics*` group, so he can see the charts,
while for other users (if you added right access permissions on page and added portlets in page),
he will see only his data without being able to change charts settings.
In fact, the user and space filtering on statistics data is made automatically switch loggedin user and page context (inside a space or in public page)

The default collected statistics data are:
* [Activities statistics data](https://github.com/Meeds-io/analytics/blob/master/analytics-listeners/src/main/java/org/exoplatform/analytics/listener/AnalyticsActivityListener.java) :
  * create activity
  * create comment
  * update activity
  * update comment
  * like activity
  * like comment.
* [Space statistics data](https://github.com/Meeds-io/analytics/blob/master/analytics-listeners/src/main/java/org/exoplatform/analytics/listener/SpaceAnalyticsListener.java):
  * create a space
  * remove a space
  * rename a space
  * update space description
  * update space registration
  * update space access
  * update space banner
  * update space avatar
  * add space application
  * disable space application
  * delete space application
  * grant space member as manager
  * revoke space member as manager
  * joining a space
  * leaving a space
  * inviting a user to a space
  * requesting join a space by a user
* [User statistics data](https://github.com/Meeds-io/analytics/blob/master/analytics-listeners/src/main/java/org/exoplatform/analytics/listener/UserAnalyticsEventListener.java):
  * Create a user
  * Delete a user
  * Enable/Disable a user
* [User Profile statistics data](https://github.com/Meeds-io/analytics/blob/master/analytics-listeners/src/main/java/org/exoplatform/analytics/listener/AnalyticsProfileListener.java):
  * create new user social profile (generally triggered at same time than create a user event)
  * update avatar
  * update banner
  * update contact section
  * update experience section
* [User login statistics data](https://github.com/Meeds-io/analytics/blob/master/analytics-listeners/src/main/java/org/exoplatform/analytics/listener/LoginAnalyticsListener.java):
  * login / logout of a user
* [User relationship statistics data](https://github.com/Meeds-io/analytics/blob/master/analytics-listeners/src/main/java/org/exoplatform/analytics/listener/AnalyticsRelationshipListener.java):
  * request connection with other user
  * accept connection with other user
  * deny connection with other user
  * ignore connection request of other user
  * remove connection with other user
* [Displayed page statistics data](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-listeners/src/main/java/org/exoplatform/analytics/listener/PageAccessListener.java)
* [Extensible UI and DOM Events statistics data](https://github.com/Meeds-io/analytics/blob/master/analytics-listeners/src/main/java/org/exoplatform/analytics/listener/WebSocketUIStatisticListener.java):
  * [Home link click](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L8-L84)
  * [Hamburger menu topbar click](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L436-L498)
  * [Hamburger menu page link click](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L85-L158)
  * [Hamburger menu space link click](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L159-L232)
  * [Post activity using drawer composer](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L291-L362)
  * [Post activity using inpage composer](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L363-L435)

Technical note: By default, the produced and persisted `statistic samples/data` by those `listeners`
has basic properties stored into it and that could be extended any time because a dynamic mapping is used on each daily
created Elasticsearch index.

The default produced data for all events in general:
* `id` : Technical id of statistics sample
* `spaceId` : Technical id of space (space name is displayed in UI in list of samples)
 (Perspective: could be retrieved using suggester for data filtering and charts setttings)
* `userId` : Technical id of user making the event (user display name is displayed in UI in list of samples)
 (Perspective: could be retrieved using suggester for data filtering and charts setttings)
* `module` : eXo project name: `social`, `portal`...
* `subModule` : `activity`, `space`, `login`, `relationship`, `profile`, `relationship`...
* `operation` : type of made operation, like `createActivity`, `login`, `logout`, `login`, `confirmed`...
* `status` : status of operation `OK` if operation succeeded else `KO`
* `timestamp` : time of statistic event in ms

Specifically for each listener, it has default produced data are: (data can be extend, like adding number of likes, comments...)
* Activity statistic data:
  * `module` = `social`
  * `subModule` = `activity`
  * `operation` =
    * `createActivity`
    * `updateActivity`
    * `createComment`
    * `updateComment`
    * `likeActivity`
    * `likeComment`
  * `streamIdentityId` : Technical identity ID of stream owner (Space ID or User ID)
   (Perspective: could be retrieved using suggester for data filtering and charts setttings)
  * `activityId` : Technical activity ID
  * `commentId` : Technical comment ID
  * `subCommentId` : Technical sub comment ID
  * `activityType` : activity type, like : `exosocial:people`, `USER_PROFILE_ACTIVITY`... (See complete list of [actitivy types](https://docs.exoplatform.org/en/latest/Configuration.html#enabling-disabling-any-activity-type))
* Space statistic data:
  * `module` = `social`
  * `subModule` = `space`
  * `operation` =
    * `spaceAccessEdited`
    * `spaceBannerEdited`
    * `spaceCreated`
    * `spaceRemoved`
    * `spaceRenamed`
    * `spaceDescriptionEdited`
    * `spaceRegistrationEdited`
    * `spaceAvatarEdited`
    * `applicationActivated`
    * `applicationDeactivated`
    * `applicationRemoved`
    * `grantedLead`
    * `joined`
    * `revokedLead`
    * `addInvitedUser`
    * `addPendingUser`
  * `modifierUserId`: Technical identity ID of user having modified portlet
   (Perspective: could be retrieved using suggester for data filtering and charts setttings)
  * `spaceTemplate`: space template name
* Login statistic data:
  * `module` = `portal`
  * `subModule` = `login`
  * `operation` =
    * `login`
    * `logout`
* User statistic data:
  * `module` = `organization`
  * `subModule` = `user`
  * `operation` =
    * `createUser`
    * `saveUser`
    * `enableUser`
    * `deleteUser`
  * `isEnabled` : if user is enabled
* Profile statistic data:
  * `module` = `social`
  * `subModule` = `profile`
  * `operation` =
    * `avatar`
    * `banner`
    * `contactSection`
    * `experienceSection`
    * `createProfile`
* Relationship statistic data:
  * `module` = `social`
  * `subModule` = `relationship`
  * `operation` =
    * `avatar`
    * `banner`
    * `contactSection`
    * `experienceSection`
    * `createProfile`
  * `from`: Technical identity ID of user (Perspective: could be retrieved using suggester for data filtering and charts setttings)
  * `to` : Technical identity ID of user (Perspective: could be retrieved using suggester for data filtering and charts setttings)
* Displayed page statistics data
  * `module` = `portal`
  * `subModule` = 
    * `webui` (in case of WebUI ajax query)
    * `page` (in case of page display)
  * `operation` = 
    * `ajaxRequest` (in case of WebUI ajax query)
    * `pageDisplay` (in case of page display)
  * `spaceTemplate` = Current space template
  * `userLocale` = Current user locale
  * `portalOwner` = Current PORTAL site (dw, intranet, ...)
  * `portalUri` = Current URI
  * `pageUri` = Current page URI (without portal URI, like 'documents', 'activities', 'wiki/user/root' )
  * `pageName` = current page name/uri last part
  * `httpRequestMethod`
  * `httpRequestUri`
  * `httpRequestProtocol`
  * `httpRequestMethod` 
  * `httpRequestContentType`
  * `httpRequestContentLength`
  * `httpResponseContentType`
  * `httpResponseContentLength`
  * `httpResponseStatus`
* Extensible **UI and DOM Events** statistics data:
  * `module` = `portal`
  * `subModule` = `ui`
  * `portalUri` = current portal URI when user triggered the event
  * For **DOM event** *Home page link* :
    * `watcher` = `Home page link` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L18))
    * `ui` = `toolbar` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L32))
    * `application` = `NavigationToolbarPortlet` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L40))
    * `operation` = `click` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L22))
    * `dom-href` = link of target page (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L71))
    * `dom-class` = DOM class value of element having triggered the event(defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L74))
    * `dom-id` = DOM class value of element having triggered the event(defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L77))
    * `event-type` = event type having triggered the event (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L57))
    * `event-pageX`: horizontal coordinate of Element having triggered the event (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L69))
    * `event-pageY`: vertical coordinate of Element having triggered the event  (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L63))
  * For **DOM event** *Hamburger menu icon* :
    * `watcher` = `Hamburger menu icon` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L437))
    * `ui` = `toolbar` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L460))
    * `application` = `HamburgerNavigationMenuLink` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L468))
    * `operation` = `click` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L450))
    * `event-type` = event type having triggered the event (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L485))
    * `event-pageX`: horizontal coordinate of Element having triggered the event (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L488))
    * `event-pageY`: vertical coordinate of Element having triggered the event  (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L491))
  * For **DOM event** *Hamburger menu site navigation link* :
    * `watcher` = `Hamburger menu site navigation link` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L95))
    * `ui` = `HamburgerMenu` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L109))
    * `application` = `SiteHamburgerNavigation` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L117))
    * `operation` = `click` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L99))
    * `dom-href` = link of target page (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L148))
    * `dom-data-aria-selected` = DOM aria-selected data value, used to determine whether the element is current page or not. (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L151))
    * `event-type` = event type having triggered the event (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L134))
    * `event-pageX`: horizontal coordinate of Element having triggered the event (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L137))
    * `event-pageY`: vertical coordinate of Element having triggered the event  (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L140))
  * For **DOM event** *Hamburger menu spaces navigation link* :
    * `watcher` = `Hamburger menu spaces navigation link` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L169))
    * `ui` = `HamburgerMenu` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L183))
    * `application` = `SpacesHamburgerNavigation` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L191))
    * `operation` = `click` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L173))
    * `dom-href` = link of target page (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L222))
    * `dom-data-aria-selected` = DOM aria-selected data value, used to determine whether the element is current page or not. (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L225))
    * `event-type` = event type having triggered the event (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L208))
    * `event-pageX`: horizontal coordinate of Element having triggered the event (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L211))
    * `event-pageY`: vertical coordinate of Element having triggered the event  (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L214))
  * For **DOM event** *Activities refresh button* :
    * `watcher` = `Activities refresh button` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L243))
    * `ui` = `ActivityStream` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L257))
    * `application` = `uiActivitiesDisplay` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L265))
    * `operation` = `click` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L247))
    * `event-type` = event type having triggered the event (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L282))
  * For **DOM event** *Post activity using Composer Drawer* :
    * `watcher` = `Post activity using Composer Drawer` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L300))
    * `ui` = `ActivityStream` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L314))
    * `component` = `composer` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L330))
    * `application` = `ActivityComposerDrawer` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L322))
    * `operation` = `click` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L304))
    * `event-type` = event type having triggered the event (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L347))
    * `dom-disabled`: DOM value of attribute `disabled` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L355))
  * For **DOM event** *Post activity using legacy Composer* :
    * `watcher` = `Post activity using legacy Composer` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L373))
    * `ui` = `ActivityStream` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L384))
    * `component` = `composer` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L330))
    * `application` = `EmbeddedActivityComposer` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L395))
    * `operation` = `click` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L377))
    * `event-type` = event type having triggered the event (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L420))
    * `dom-disabled`: DOM value of attribute `disabled` (defined by [configuration](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml#L428))


You can consult the data used to display analytics chart using `View samples` button on each portlet.
By viewing samples, you will be able to know what `fields` to use in settings to :
* choose X Axis field (date by default)
  * Per day
  * Per week
  * Per month
  * Per quarter
  * Per year
  * Per custom field : you can choose a custom field from the selectbox:
  for now, the fields are retrieved without name, just its technical name that you can find in displayed `statistic samples/data` in drawer.
  For example, if you choose `spaceId`, then in the X Axis, you will get the space name.
* choose Y Axis:
  * *count* of `statistic samples/data` per choosen X Axis field (date period or custom field)
  * *count samples with distinct fields*.
  For example, to get the number logins per 'unique users' per day, you will choose `Per day` as X Axis and this option as Y Axis.
  * *sum of* `statistic samples/data numeric field` : this will display in Y Axis the sum of a specific `Numeric` field.
  * *average of* `statistic samples/data numeric field` : this will display in Y Axis the average rate of a specific `Numeric` field.
* Data filters : a list of conditions on to filter on used data in computing results
* Multiple charts :
  * When enabled, this will display multiple charts in one single.

# Examples of settings

For example, if you want to display the number of created activities per day and per space (pay attention to the number of spaces).

ou can choose the following parameters:
  * X Axis = `Per day`
  * Y Axis = `count samples`
  * Multiple charts = true, in addition choose field `spaceId` (a chart will be retrieved per spaceId)
  * Data filters :
   ** `operation` (`equals`) `createActivity`
   ** `spaceId` (`greater`) `1` (or if you have a lot of spaces, you will have to choose a set of `spaceId`, like :
   `spaceId` (`in set`) `1,2,3,895`)

# DOM Events configuration plugin

In order to add new DOM Event watcher, you can add a [component-plugin](https://docs.exoplatform.org/en/5.3/eXo_Kernel.html#external-plugin) to inject into Service `org.exoplatform.analytics.api.service.AnalyticsService`. (You can follow some example from [here](https://github.com/Meeds-io/analytics/blob/1.0.0-M04/analytics-webapps/src/main/webapp/WEB-INF/conf/analytics/analytics-ui-watchers-configuration.xml))

The DOM statistic data are collected in a sync way, but sent to server in async way in a dedicated Process using [Cometd Worker](https://docs.cometd.org/current3/reference/#_javascript_configure) (used parameter: **`useWorkerScheduler`**)

To understand more about properties of plugin, please read the commented description in the following example: 
```xml
    <component-plugin>
      <name>Home page link</name>
      <set-method>addUIWatcherPlugin</set-method>
      <type>org.exoplatform.analytics.api.service.StatisticUIWatcherPlugin</type>
      <init-params>
        <object-param>
          <name>watcher</name>
          <object type="org.exoplatform.analytics.api.service.StatisticWatcher">
            <!-- CLIENT SIDE: DOM jquery selector, used to install watcher in UI to trigger new statistic data item to server -->
            <field name="domSelector">
              <string>#brandingTopBar a</string>
            </field>
            <!-- CLIENT SIDE: DOM jquery event that will be used to listen on selected element -->
            <field name="domEvent">
              <string>mousedown</string>
            </field>
            <!-- GENERATED STATISTIC DATA PROPERTY: `name` field -->
            <field name="name">
              <string>Home page link</string>
            </field>
            <!-- GENERATED STATISTIC DATA PROPERTY: `operation` field -->
            <field name="operation">
              <string>click</string>
            </field>
            <!-- GENERATED STATISTIC DATA PROPERTY: additional embedded parameters to include in generated statistic data. This can be useful to classify triggired event by category, sub-category... This will help to make easier aggregations on data -->
            <field name="parameters">
              <map type="java.util.HashMap">
                <entry>
                  <key>
                    <string>ui</string>
                  </key>
                  <value>
                    <string>toolbar</string>
                  </value>
                </entry>
                <entry>
                  <key>
                    <string>application</string>
                  </key>
                  <value>
                    <string>NavigationToolbarPortlet</string>
                  </value>
                </entry>
              </map>
            </field>
            <!-- GENERATED STATISTIC DATA PROPERTY: used to store additional information in generated statistic data. This will get the following properties of `DOM event` object -->
            <field name="domEventProperties">
              <collection type="java.util.ArrayList" item-type="java.lang.String">
                <value>
                  <string>type</string>
                </value>
                <value>
                  <string>pageX</string>
                </value>
                <value>
                  <string>pageY</string>
                </value>
              </collection>
            </field>
            <!-- GENERATED STATISTIC DATA PROPERTY: used to store additional information in generated statistic data. This list of DOM properties will be retrieved using jQuery(...).attr(), else if not exists, jQuery(...).data() will be used -->
            <field name="domProperties">
              <collection type="java.util.ArrayList" item-type="java.lang.String">
                <value>
                  <string>href</string>
                </value>
                <value>
                  <string>class</string>
                </value>
                <value>
                  <string>id</string>
                </value>
              </collection>
            </field>
          </object>
        </object-param>
      </init-params>
    </component-plugin>
```