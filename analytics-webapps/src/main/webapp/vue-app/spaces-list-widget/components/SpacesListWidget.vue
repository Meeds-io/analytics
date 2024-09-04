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
  <v-app>
    <v-hover v-model="hover">
      <widget-wrapper :loading="loading" class="fill-height">
        <template #title>
          <div class="d-flex flex-grow-1 flex-shrink-1 full-width align-center position-relative">
            <div
              v-if="!emptyWidget"
              class="widget-text-header text-none text-truncate d-flex align-center">
              {{ $t('analytics.spacesListWidget.header') }}
            </div>
            <div
              :class="{
                'mt-2 me-2': emptyWidget,
                'l-0': $vuetify.rtl,
                'r-0': !$vuetify.rtl,
              }"
              class="position-absolute absolute-vertical-center z-index-one">
              <v-btn
                v-if="!emptyWidget && !$root.isExternal"
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
          <div v-else class="mt-4">
            <spaces-list-widget-list
              :list="mostRecentSpaces"
              label-key="analytics.spacesListWidget.recentSpaces" />
            <spaces-list-widget-list
              :list="mostActiveSpaces"
              label-key="analytics.spacesListWidget.activeSpaces" />
          </div>
        </template>
      </widget-wrapper>
    </v-hover>
    <spaces-list-widget-settings-drawer
      v-if="$root.canEdit" />
    <spaces-list-widget-drawer
      v-if="!$root.isExternal"
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
      return (this.mostRecentSpaces?.length || 0) + (this.mostActiveSpaces?.length || 0);
    },
    emptyWidget() {
      return !this.spacesCount && this.initialized && this.applicationMounted;
    },
    spacesRecentlyVisitedLimit() {
      return this.$root.spacesRecentlyVisitedLimit;
    },
    spacesMostActiveLimit() {
      return this.$root.spacesMostActiveLimit;
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
      return Promise.all([
        this.getRecentyVisitedSpaces(),
        this.getMostActiveSpaces(),
      ]).finally(() => this.loading = false);
    },
    getRecentyVisitedSpaces() {
      if (!this.$root.spacesRecentlyVisitedLimit) {
        this.mostRecentSpaces = null;
        return Promise.resolve();
      }
      return this.getSpaces('spacesList.recentlyVisitedURL', this.$root.spacesRecentlyVisitedLimit)
        .then(data => this.mostRecentSpaces = data?.labels);
    },
    getMostActiveSpaces() {
      if (!this.$root.spacesMostActiveLimit || this.$root.isExternal) {
        this.mostActiveSpaces = null;
        return Promise.resolve();
      }
      return this.getSpaces('spacesList.mostActive', this.$root.spacesMostActiveLimit)
        .then(data => this.mostActiveSpaces = data?.labels);
    },
    getSpaces(queryName, limit) {
      return fetch(`${this.$root.resourceURL}&queryName=${queryName}&xLimit=${limit}`)
        .then(resp => resp?.ok && resp.json());
    },
  },
};
</script>