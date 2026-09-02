<!--

  This file is part of the Meeds project (https://meeds.io/).

  Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 3 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

-->
<template>
  <exo-drawer
    ref="drawer"
    v-model="drawer"
    :loading="loading"
    :right="!$vuetify.rtl"
    class="spacesListOverviewDrawer"
    allow-expand>
    <template #title>
      <div class="d-flex justify-space-between">
        <span> {{ memberSpacesOnly && $t('analytics.spacesListWidget.tab.userSpaces') || $t('analytics.spacesListWidget.drawer.title') }} </span>
        <space-creation-button
          v-if="$root.canCreateSpace && (!profileMode || ownProfile)"
          require-form-drawer
          display-label
          :color="'primary'"
          :elevation="0"
          :parent-space-id="$root.isParentSpace && $root.spaceId || null"
          :display-icon="false" />
      </div>
    </template>
    <template v-if="drawer" #content>
      <v-tabs
        v-if="profileMode && !singleTab"
        v-model="tabName"
        slider-size="4">
        <v-tab
          tab-value="common"
          href="#common">
          {{ $t('analytics.spacesListWidget.tab.common') }}
        </v-tab>
        <v-tab
          tab-value="all"
          href="#all">
          {{ $t('analytics.spacesListWidget.tab.all') }}
        </v-tab>
      </v-tabs>
      <v-tabs
        v-else-if="!profileMode && !memberSpacesOnly"
        v-model="tabName"
        slider-size="4">
        <v-tab
          tab-value="member"
          href="#member">
          {{ $t('analytics.spacesListWidget.tab.userSpaces') }}
        </v-tab>
        <v-tab
          tab-value="visited"
          href="#visited">
          {{ $t('analytics.spacesListWidget.tab.visited') }}
        </v-tab>
        <v-tab
          v-if="!$root.isExternal"
          tab-value="mostActive"
          href="#mostActive">
          {{ $t('analytics.spacesListWidget.tab.mostActive') }}
        </v-tab>
      </v-tabs>
      <v-list
        v-if="profileMode && singleTab"
        class="ma-5">
        <spaces-list-widget-list :list="list" />
      </v-list>
      <v-tabs-items
        v-else-if="profileMode"
        v-model="tabName"
        class="px-4">
        <v-tab-item value="common">
          <v-list v-if="commonSpaces">
            <spaces-list-widget-list :list="commonSpaces" />
          </v-list>
        </v-tab-item>
        <v-tab-item value="all">
          <v-list v-if="allSpaces">
            <spaces-list-widget-list :list="allSpaces" />
          </v-list>
        </v-tab-item>
      </v-tabs-items>
      <v-list
        v-else-if="memberSpacesOnly"
        class="ma-5">
        <spaces-list-widget-list :list="memberSpacesToDisplay" />
      </v-list>
      <v-tabs-items
        v-else
        v-model="tabName"
        class="px-4">
        <v-tab-item value="member">
          <v-list v-if="memberSpaces">
            <spaces-list-widget-list :list="memberSpacesToDisplay" />
          </v-list>
        </v-tab-item>
        <v-tab-item value="visited">
          <v-list v-if="visitedSpaces">
            <spaces-list-widget-list :list="visitedSpacesToDisplay" />
          </v-list>
        </v-tab-item>
        <v-tab-item v-if="!$root.isExternal" value="mostActive">
          <v-list v-if="activeSpaces">
            <spaces-list-widget-list :list="activeSpacesToDisplay" />
          </v-list>
        </v-tab-item>
      </v-tabs-items>
      <div v-if="emptyList" class="d-flex flex-column align-center justify-center full-width">
        <v-icon color="tertiary" size="40">{{ profileMode && tabName === 'common' && !ownProfile && 'fa-layer-group' || 'fa-people-arrows' }}</v-icon>
        <span class="mt-5">{{ profileMode && tabName === 'common' && !ownProfile && $t('analytics.spacesListWidget.noCommonSpaces') || $t('analytics.spacesListWidget.noSpaces') }}</span>
      </div>
    </template>
    <template v-if="drawer && hasMore" slot="footer">
      <v-spacer />
      <v-btn
        :loading="loading"
        :disabled="loading"
        class="loadMoreButton ma-auto btn"
        block
        @click="loadMore">
        {{ $t('analytics.spacesListWidget.loadMore') }}
      </v-btn>
      <v-spacer />
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    memberSpacesOnly: {
      type: Boolean,
      default: false,
    }
  },
  data: () => ({
    drawer: false,
    loading: false,
    tabName: 'member',
    pageSize: 0,
    limit: 0,
    memberSpaces: null,
    visitedSpaces: null,
    activeSpaces: null,
    commonSpaces: null,
    allSpaces: null,
    profileHasMore: {},
    profilePending: {},
  }),
  computed: {
    profileMode() {
      return !!this.$root.profileOwner;
    },
    ownProfile() {
      return this.profileMode && this.$root.profileOwner === eXo.env.portal.userName;
    },
    singleTab() {
      // One tab on one's own profile and whenever either side is external
      // (board stories US05, US03 and US07); two tabs — common / all — for an
      // internal viewer on an internal profile (US06)
      return this.ownProfile || this.$root.isExternal || this.$root.profileOwnerExternal;
    },
    list() {
      switch (this.tabName) {
      case 'visited': return this.visitedSpaces;
      case 'mostActive': return this.activeSpaces;
      case 'member': return this.memberSpaces;
      case 'common': return this.commonSpaces;
      case 'all': return this.allSpaces;
      default: return null;
      }
    },
    emptyList() {
      return !this.list?.length;
    },
    memberSpacesToDisplay() {
      return this.memberSpaces?.slice?.(0, this.limit);
    },
    visitedSpacesToDisplay() {
      return this.visitedSpaces?.slice?.(0, this.limit);
    },
    activeSpacesToDisplay() {
      return this.activeSpaces?.slice?.(0, this.limit);
    },
    hasMore() {
      if (this.profileMode) {
        return !!this.profileHasMore[this.tabName];
      }
      return this.list && this.list.length > this.limit;
    },
    listOnlySubSpaces() {
      return this.$root.listOnlySubSpaces;
    },
    parentSpaceId() {
      return this.$root.spaceId;
    },
    appendParentSpaceParam() {
      return !!(this.listOnlySubSpaces && this.parentSpaceId);
    },
  },
  watch: {
    tabName() {
      if (this.profileMode) {
        // Each tab keeps its accumulated pages; only an empty tab fetches. No
        // early return on loading: retrieveProfileSpaces captures its tab and
        // writes only into that tab's accumulator, so a fetch started while the
        // other tab is still loading is safe — the early return would leave a
        // tab switched to during a load permanently unfetched, wearing the
        // empty state as if it were data.
        if (!this.list?.length) {
          this.retrieveProfileSpaces();
        }
      } else if (!this.loading) {
        this.retrieveList(true);
      }
    },
  },
  methods: {
    open(tabName) {
      if (this.profileMode) {
        // Own profile: the single tab lists everything (US05). Anyone else
        // starts on the shared spaces (US06's first tab, US03/US07's only one).
        this.tabName = this.ownProfile ? 'all' : 'common';
        // The specification paginates the drawer 20 per page (note 50524 §2)
        this.pageSize = 20;
      } else {
        this.tabName = tabName || 'member';
        this.pageSize = parseInt((window.innerHeight - 180) / 56);
      }
      this.$refs.drawer.open();
      this.retrieveList(true);
    },
    loadMore() {
      if (this.profileMode) {
        this.retrieveProfileSpaces();
        return;
      }
      this.limit += this.pageSize;
      this.retrieveList();
    },
    retrieveList(reset) {
      if (this.profileMode) {
        if (reset) {
          this.commonSpaces = null;
          this.allSpaces = null;
          this.profileHasMore = {};
        }
        return this.retrieveProfileSpaces();
      }
      if (reset) {
        this.limit = this.pageSize;
        this.memberSpaces = null;
        this.visitedSpaces = null;
        this.activeSpaces = null;
      }
      this.loading = true;
      if (this.tabName === 'member') {
        return this.getUserSpaces()
          .finally(() => this.loading = false);
      } else if (this.tabName === 'mostActive') {
        return this.getMostActiveSpaces()
          .finally(() => this.loading = false);
      } else {
        return this.getRecentyVisitedSpaces()
          .finally(() => this.loading = false);
      }
    },
    retrieveProfileSpaces() {
      // Offset-paged accumulation: one page fetched per click, one extra row
      // asked to know whether more remain. The scope is the tab; the Service
      // still narrows it for a viewer who may not use it.
      const tab = this.tabName;
      if (this.profilePending[tab]) {
        // One in-flight page per tab. open() resets and fetches the initial
        // tab, then the tabName watcher fires for that same tab and would
        // request the first page a second time; a switch to the other tab
        // during a load still fetches, since the guard is per tab and not
        // the shared loading flag.
        return Promise.resolve();
      }
      const accumulated = (tab === 'all' ? this.allSpaces : this.commonSpaces) || [];
      const scope = tab === 'all' || this.ownProfile ? 'ALL' : 'COMMON';
      this.profilePending = {...this.profilePending, [tab]: true};
      this.loading = true;
      return this.$spaceService.getUserSpaces(this.$root.profileOwner, accumulated.length, this.pageSize + 1, scope)
        .then(data => {
          const spaces = data?.spaces || [];
          this.profileHasMore = {...this.profileHasMore, [tab]: spaces.length > this.pageSize};
          // Offset paging over a live listing: a membership change between two
          // pages can re-serve an already accumulated space, and a duplicate
          // would collide on the list's :key. Deduplicate on the space id; the
          // symmetric skipped row is inherent to offset paging and bounded by
          // the reset on every drawer opening.
          const accumulatedIds = new Set(accumulated.map(space => space.id));
          const page = accumulated.concat(spaces.slice(0, this.pageSize).filter(space => !accumulatedIds.has(space.id)));
          if (tab === 'all') {
            this.allSpaces = page;
          } else {
            this.commonSpaces = page;
          }
        })
        .catch(error => {
          // eslint-disable-next-line no-console
          console.error('Error listing the spaces of the profile owner', error);
        })
        .finally(() => {
          this.profilePending = {...this.profilePending, [tab]: false};
          this.loading = false;
        });
    },
    getUserSpaces() {
      return this.$spaceService.getSpacesByFilter({
        query: null,
        offset: 0,
        limit: this.limit +1,
        filter: 'member',
        expand: 'spaceId',
        parentSpaceId: this.appendParentSpaceParam && this.parentSpaceId || null
      })
        .then(data => this.memberSpaces = data?.spaces?.map(s => s.id) || []);
    },
    getRecentyVisitedSpaces() {
      if (this.$root.spacesMemberOf && !this.$root.spaceIds?.length) {
        this.visitedSpaces = null;
        return Promise.resolve();
      }
      return this.getSpaces('spacesList.recentlyVisitedURL', this.$root.spacesRecentlyVisitedPeriod, this.limit + 1)
        .then(data => this.visitedSpaces = data?.labels);
    },
    getMostActiveSpaces() {
      if (this.$root.spacesMemberOf && !this.$root.spaceIds?.length) {
        this.activeSpaces = null;
        return Promise.resolve();
      }
      return this.getSpaces('spacesList.mostActive', this.$root.spacesMostActivePeriod, this.limit + 1)
        .then(data => this.activeSpaces = data?.labels);
    },
    getSpaces(queryName, period, limit) {
      if (this.$root.spacesMemberOf) {
        queryName += '.memberOnly';
      }
      const fromTimestamp = this.getPeriodTimestamp(period);
      let fetchUrl = `${this.$root.resourceURL}&queryName=${queryName}&xLimit=${limit}&fromTimestamp=${fromTimestamp}`;
      if (this.appendParentSpaceParam) {
        fetchUrl = `${fetchUrl}&parentSpaceId=${this.parentSpaceId}`;
      }
      return fetch(fetchUrl)
        .then(resp => resp?.ok && resp.json());
    },
    getPeriodTimestamp(period) {
      return Date.now() - period * 86400000;
    },
  }
};
</script>
