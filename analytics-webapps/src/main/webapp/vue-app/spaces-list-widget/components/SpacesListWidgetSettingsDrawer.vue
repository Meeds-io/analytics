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
    :right="!$vuetify.rtl"
    :loading="loading"
    eager
    @closed="reset">
    <template slot="title">
      {{ $t('analytics.spacesListWidgetSettings.title') }}
    </template>
    <template v-if="drawer" #content>
      <div class="d-flex align-center text-start pa-5">
        <div>{{ $t('analytics.spacesListWidget.settings.spacesMemberOf') }}</div>
        <v-switch
          v-model="spacesMemberOf"
          class="ms-auto me-n1 mt-0 mb-n2 pa-0" />
      </div>
      <div class="d-flex align-center text-start px-5 pb-5">
        {{ $t('analytics.spacesListWidgetSettings.numberOfItemsToList') }}
      </div>
      <div class="d-flex align-center text-start px-5">
        <div class="font-weight-bold">{{ $t('analytics.spacesListWidget.settings.userSpaces') }}</div>
        <number-input
          v-model="userSpacesLimit"
          :min="0"
          :max="25"
          :step="1"
          class="ms-auto me-n1 my-0 pa-0" />
      </div>
      <div class="d-flex align-center text-start px-5 pt-5 pb-2">
        <div class="font-weight-bold">{{ $t('analytics.spacesListWidget.settings.recentSpaces') }}</div>
      </div>
      <div class="d-flex align-center text-start px-5 pb-2">
        <div>{{ $t('analytics.spacesListWidget.settings.numberOfSpaces') }}</div>
        <number-input
          v-model="spacesRecentlyVisitedLimit"
          :min="0"
          :max="25"
          :step="1"
          class="ms-auto me-n1 my-0 pa-0" />
      </div>
      <div v-if="spacesRecentlyVisitedLimit" class="d-flex align-center text-start px-5">
        <div>{{ $t('analytics.spacesListWidget.activePeriod') }}</div>
        <number-input
          v-model="spacesRecentlyVisitedPeriod"
          :min="1"
          :max="90"
          :step="1"
          :unit="$t('analytics.spacesListWidget.days')"
          class="ms-auto me-n1 my-0 pa-0" />
      </div>
      <div class="d-flex align-center text-start px-5 pt-5">
        <div class="font-weight-bold">{{ $t('analytics.spacesListWidget.settings.activeSpaces') }}</div>
      </div>
      <div class="d-flex align-center text-start px-5 pb-2">
        <div>{{ $t('analytics.spacesListWidget.settings.numberOfSpaces') }}</div>
        <number-input
          v-model="spacesMostActiveLimit"
          :min="0"
          :max="25"
          :step="1"
          class="ms-auto me-n1 my-0 pa-0" />
      </div>
      <div v-if="spacesMostActiveLimit" class="d-flex align-center text-start px-5">
        <div>{{ $t('analytics.spacesListWidget.activePeriod') }}</div>
        <number-input
          v-model="spacesMostActivePeriod"
          :min="1"
          :max="90"
          :step="1"
          :unit="$t('analytics.spacesListWidget.days')"
          class="ms-auto me-n1 my-0 pa-0" />
      </div>
    </template>
    <template #footer>
      <div class="d-flex align-center text-start">
        <v-btn
          :disabled="loading"
          :title="$t('links.label.cancel')"
          class="btn ms-auto me-2"
          @click="close()">
          {{ $t('analytics.spacesListWidgetSettings.cancel') }}
        </v-btn>
        <v-btn
          :loading="loading"
          color="primary"
          elevation="0"
          @click="save()">
          {{ $t('analytics.spacesListWidgetSettings.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
    loading: false,
    spacesMemberOf: true,
    userSpacesLimit: 0,
    spacesRecentlyVisitedPeriod: 0,
    spacesRecentlyVisitedLimit: 0,
    spacesMostActiveLimit: 0,
    spacesMostActivePeriod: 0,
  }),
  created() {
    this.$root.$on('spaces-list-widget-settings', this.open);
  },
  beforeDestroy() {
    this.$root.$off('spaces-list-widget-settings', this.open);
  },
  methods: {
    open() {
      this.reset();
      this.$refs.drawer.open();
    },
    reset() {
      this.spacesMemberOf = this.$root.spacesMemberOf || false;
      this.userSpacesLimit = this.$root.userSpacesLimit;
      this.spacesRecentlyVisitedLimit = this.$root.spacesRecentlyVisitedLimit;
      this.spacesRecentlyVisitedPeriod = this.$root.spacesRecentlyVisitedPeriod;
      this.spacesMostActiveLimit = this.$root.spacesMostActiveLimit;
      this.spacesMostActivePeriod = this.$root.spacesMostActivePeriod;
      this.loading = false;
    },
    close() {
      this.$refs.drawer.close();
    },
    save() {
      this.loading = true;
      const formData = new FormData();
      formData.append('pageRef', this.$root.pageRef);
      formData.append('applicationId', this.$root.portletStorageId);
      const params = new URLSearchParams(formData).toString();
      return fetch(`/layout/rest/pages/application/preferences?${params}`, {
        method: 'PATCH',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          preferences: [{
            name: 'spacesMemberOf',
            value: String(this.spacesMemberOf),
          }, {
            name: 'userSpacesLimit',
            value: String(this.userSpacesLimit),
          }, {
            name: 'spacesRecentlyVisitedLimit',
            value: String(this.spacesRecentlyVisitedLimit),
          }, {
            name: 'spacesRecentlyVisitedPeriod',
            value: String(this.spacesRecentlyVisitedPeriod),
          }, {
            name: 'spacesMostActiveLimit',
            value: String(this.spacesMostActiveLimit),
          }, {
            name: 'spacesMostActivePeriod',
            value: String(this.spacesMostActivePeriod),
          }],
        }),
      })
        .then(() => {
          this.$root.spacesMemberOf = this.spacesMemberOf;
          this.$root.userSpacesLimit = this.userSpacesLimit;
          this.$root.spacesRecentlyVisitedLimit = this.spacesRecentlyVisitedLimit;
          this.$root.spacesRecentlyVisitedPeriod = this.spacesRecentlyVisitedPeriod;
          this.$root.spacesMostActiveLimit = this.spacesMostActiveLimit;
          this.$root.spacesMostActivePeriod = this.spacesMostActivePeriod;
          this.close();
        })
        .finally(() => this.loading = false);
    },
  },
};
</script>
