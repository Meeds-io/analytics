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
      {{ $t('analytics.spacesListWidget.drawer.title') }}
    </template>
    <template v-if="drawer" #content>
      <v-tabs
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
      <v-tabs-items
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
  data: () => ({
    drawer: false,
    loading: false,
    tabName: 'member',
    pageSize: 0,
    limit: 0,
    memberSpaces: null,
    visitedSpaces: null,
    activeSpaces: null,
  }),
  computed: {
    list() {
      switch (this.tabName) {
      case 'visited': return this.visitedSpaces;
      case 'mostActive': return this.activeSpaces;
      case 'member': return this.memberSpaces;
      default: return null;
      }
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
      return this.list && this.list.length > this.limit;
    },
  },
  watch: {
    tabName() {
      if (!this.loading) {
        this.retrieveList(true);
      }
    },
  },
  methods: {
    open(tabName) {
      this.tabName = tabName || 'member';
      this.pageSize = parseInt((window.innerHeight - 180) / 56);

      this.$refs.drawer.open();
      this.retrieveList(true);
    },
    loadMore() {
      this.limit += this.pageSize;
      this.retrieveList();
    },
    retrieveList(reset) {
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
    getUserSpaces() {
      return this.$spaceService.getSpaces(null, 0, this.limit + 1, 'member', 'spaceId')
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
      const fromTimestamp = this.getPeriodTimestamp(period);
      return fetch(`${this.$root.resourceURL}&queryName=${queryName}&xLimit=${limit}&fromTimestamp=${fromTimestamp}`)
        .then(resp => resp?.ok && resp.json());
    },
    getPeriodTimestamp(period) {
      return Date.now() - period * 86400000;
    },
  }
};
</script>
