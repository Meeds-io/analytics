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
  <v-app v-if="initialized">
    <v-hover v-model="hover">
      <widget-wrapper :loading="loading" class="fill-height">
        <template #title>
          <div class="d-flex flex-grow-1 flex-shrink-1 full-width align-center position-relative">
            <div
              v-if="!emptyWidget"
              class="widget-text-header text-none text-truncate d-flex align-center">
              {{ title }}
            </div>
            <div
              :class="{
                'mt-2 me-2': emptyWidget,
                'l-0': $vuetify.rtl,
                'r-0': !$vuetify.rtl,
              }"
              class="position-absolute absolute-vertical-center z-index-one">
              <v-btn
                v-if="!emptyWidget"
                :icon="hoverEdit"
                :small="hoverEdit"
                height="auto"
                min-width="auto"
                class="pa-0"
                text
                @click="$refs.listDrawer.open()">
                <v-icon
                  v-if="hoverEdit"
                  size="18"
                  color="primary">
                  fa-external-link-alt
                </v-icon>
                <span v-else class="primary--text text-none">{{ $t('analytics.spacesListWidgetSettings.seeAll') }}</span>
              </v-btn>
              <v-fab-transition hide-on-leave>
                <v-btn
                  v-show="hoverEdit"
                  :title="$t('analytics.spacesListWidgetSettings.editTooltip')"
                  small
                  icon
                  @click="$root.$emit('spaces-list-widget-settings')">
                  <v-icon size="18">fa-cog</v-icon>
                </v-btn>
              </v-fab-transition>
            </div>
          </div>
        </template>
        <template v-if="initialized" #default>
          <div v-if="emptyWidget" class="d-flex flex-column align-center justify-center full-width full-height">
            <v-icon color="tertiary" size="60">fa-people-arrows</v-icon>
            <span class="mt-5">{{ $t('analytics.spacesListWidget.noSpaces') }}</span>
            <v-btn
              v-if="$root.canCreateSpace"
              :href="createSpaceLink"
              id="addNewSpaceButton"
              color="primary"
              elevation="0"
              class="my-4">
              {{ $t('analytics.spacesListWidget.addNewSpace') }}
            </v-btn>
          </div>
          <div v-else>
            <spaces-list-widget-list
              :list="userSpaces"
              :label-key="sectionsCount > 1 && 'analytics.spacesListWidget.userSpaces'" />
            <spaces-list-widget-list
              :list="mostRecentSpaces"
              :label-key="sectionsCount > 1 && 'analytics.spacesListWidget.recentSpaces'" />
            <spaces-list-widget-list
              :list="mostActiveSpaces"
              :label-key="sectionsCount > 1 && 'analytics.spacesListWidget.activeSpaces'" />
          </div>
        </template>
      </widget-wrapper>
    </v-hover>
    <spaces-list-widget-settings-drawer
      v-if="$root.canEdit" />
    <spaces-list-widget-drawer
      ref="listDrawer" />
  </v-app>
</template>
<script>
export default {
  data: () => ({
    hover: false,
    loading: false,
    initialized: false,
    applicationMounted: false,
    mostRecentSpaces: null,
    mostActiveSpaces: null,
  }),
  computed: {
    createSpaceLink() {
      return `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/spaces?createSpace=true`;
    },
    hoverEdit() {
      return this.hover && this.$root.canEdit;
    },
    spacesCount() {
      return (this.userSpaces?.length || 0) + (this.mostRecentSpaces?.length || 0) + (this.mostActiveSpaces?.length || 0);
    },
    sectionsCount() {
      return (this.spacesRecentlyVisitedLimit && this.mostRecentSpaces?.length && 1 || 0)
        + (this.spacesMostActiveLimit && this.mostActiveSpaces?.length && 1 || 0)
        + (this.spacesMemberOf && this.userSpaces?.length && 1 || 0);
    },
    emptyWidget() {
      return !this.spacesCount && this.initialized && this.applicationMounted;
    },
    userSpacesLimit() {
      return this.$root.userSpacesLimit;
    },
    spacesRecentlyVisitedLimit() {
      return this.$root.spacesRecentlyVisitedLimit;
    },
    spacesMostActiveLimit() {
      return this.$root.spacesMostActiveLimit;
    },
    spacesMemberOf() {
      return this.$root.spacesMemberOf;
    },
    userSpaces() {
      return this.$root.userSpacesLimit && this.$root.spaceIds?.slice?.(0, this.$root.userSpacesLimit);
    },
    title() {
      if (this.sectionsCount > 1) {
        return this.$t('analytics.spacesListWidget.header');
      } else if (this.spacesMostActiveLimit) {
        return this.$t('analytics.spacesListWidget.active.header');
      } else if (this.spacesRecentlyVisitedLimit) {
        return this.$t('analytics.spacesListWidget.recent.header');
      } else if (this.userSpacesLimit) {
        return this.$t('analytics.spacesListWidget.member.header');
      } else {
        return this.$t('analytics.spacesListWidget.header');
      }
    },
  },
  watch: {
    loading() {
      if (!this.loading) {
        this.initialized = true;
      }
    },
    initialized() {
      if (this.initialized) {
        this.$root.$applicationLoaded();
      }
    },
    emptyWidget() {
      if (this.emptyWidget && !this.$root.canEdit) {
        this.$root.$updateApplicationVisibility(false);
      }
    },
    spacesRecentlyVisitedLimit() {
      if (!this.loading) {
        this.refresh();
      }
    },
    spacesMostActiveLimit() {
      if (!this.loading) {
        this.refresh();
      }
    },
    userSpacesLimit() {
      if (!this.loading) {
        this.refresh();
      }
    },
    spacesMemberOf() {
      if (!this.loading) {
        this.refresh();
      }
    },
  },
  created() {
    this.refresh();
  },
  mounted() {
    this.applicationMounted = true;
  },
  methods: {
    refresh() {
      this.loading = true;
      if (this.$root.spacesMemberOf) {
        this.loading = true;
        return this.getUserSpaces()
          .finally(() => Promise.all([
            this.getRecentyVisitedSpaces(),
            this.getMostActiveSpaces(),
          ]).finally(() => this.loading = false));
      } else {
        const getUserSpaces = this.$root.userSpacesLimit ?
          this.getUserSpaces()
          : Promise.resolve().then(() => this.$root.spaceIds = null);
        return Promise.all([
          this.getRecentyVisitedSpaces(),
          this.getMostActiveSpaces(),
          getUserSpaces,
        ]).finally(() => this.loading = false);
      }
    },
    getUserSpaces() {
      return this.$spaceService.getSpaces(null, 0, -1, 'member', 'spaceId')
        .then(data => this.$root.spaceIds = data?.spaces?.map(s => s.id) || []);
    },
    getRecentyVisitedSpaces() {
      if (!this.$root.spacesRecentlyVisitedLimit || (this.$root.spacesMemberOf && !this.$root.spaceIds?.length)) {
        this.mostRecentSpaces = null;
        return Promise.resolve();
      }
      return this.getSpaces('spacesList.recentlyVisitedURL', this.$root.spacesRecentlyVisitedPeriod, this.$root.spacesRecentlyVisitedLimit)
        .then(data => this.mostRecentSpaces = data?.labels);
    },
    getMostActiveSpaces() {
      if (!this.$root.spacesMostActiveLimit || this.$root.isExternal || (this.$root.spacesMemberOf && !this.$root.spaceIds?.length)) {
        this.mostActiveSpaces = null;
        return Promise.resolve();
      }
      return this.getSpaces('spacesList.mostActive', this.$root.spacesMostActivePeriod, this.$root.spacesMostActiveLimit)
        .then(data => this.mostActiveSpaces = data?.labels);
    },
    getSpaces(queryName, period, limit) {
      if (this.$root.spacesMemberOf) {
        queryName += '.memberOnly';
      }
      let url = `${this.$root.resourceURL}&queryName=${queryName}&xLimit=${limit}`;
      if (this.$root.spacesMemberOf) {
        const formData = new FormData();
        formData.append('spaceIds', this.$root.spaceIds.join(','));
        const params = new URLSearchParams(formData).toString();
        url += `&${params}`;
      }
      url += `&fromTimestamp=${this.getPeriodTimestamp(period)}`;
      return fetch(url)
        .then(resp => resp?.ok && resp.json());
    },
    getPeriodTimestamp(period) {
      return Date.now() - period * 86400000;
    },
  },
};
</script>