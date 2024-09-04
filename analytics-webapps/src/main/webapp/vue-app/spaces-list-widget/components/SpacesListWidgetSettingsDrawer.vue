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
      <div class="d-flex align-center text-header px-5 pt-5 pb-2">
        {{ $t('analytics.spacesListWidgetSettings.numberOfItemsToList') }}
      </div>
      <div class="d-flex align-center px-5">
        <div>{{ $t('analytics.spacesListWidgetSettings.recentlyVisited') }}</div>
        <number-input
          v-model="spacesRecentlyVisitedLimit"
          :min="0"
          :max="25"
          :step="1"
          class="ms-auto me-n1 my-0 pa-0" />
      </div>
      <div class="d-flex align-center px-5">
        <div>{{ $t('analytics.spacesListWidgetSettings.mostActive') }}</div>
        <number-input
          v-model="spacesMostActiveLimit"
          :min="0"
          :max="25"
          :step="1"
          class="ms-auto me-n1 my-0 pa-0" />
      </div>
    </template>
    <template #footer>
      <div class="d-flex align-center">
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
    spacesRecentlyVisitedLimit: 4,
    spacesMostActiveLimit: 0,
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
      this.spacesRecentlyVisitedLimit = this.$root.spacesRecentlyVisitedLimit || 4;
      this.spacesMostActiveLimit = this.$root.spacesMostActiveLimit || 0;
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
            name: 'spacesRecentlyVisitedLimit',
            value: String(this.spacesRecentlyVisitedLimit),
          }, {
            name: 'spacesMostActiveLimit',
            value: String(this.spacesMostActiveLimit),
          }],
        }),
      })
        .then(() => {
          this.$root.spacesRecentlyVisitedLimit = this.spacesRecentlyVisitedLimit;
          this.$root.spacesMostActiveLimit = this.spacesMostActiveLimit;
          this.close();
        })
        .finally(() => this.loading = false);
    },
  },
};
</script>
