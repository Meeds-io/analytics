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
  <div class="pe-4">
    <v-tooltip v-if="spaceLastActivityTime" bottom>
      <template #activator="{on, attrs}">
        <div
          v-on="on"
          v-bind="attrs">
          <date-format :value="spaceLastActivityTime" :format="dateFormat" />
        </div>
      </template>
      <date-format :value="spaceLastActivityTime" :format="fullDateFormat" />
    </v-tooltip>
    <span v-else-if="failed" :title="errorLabel">
      <v-icon class="error--text" size="16">
        fa-exclamation-circle
      </v-icon>
      <!-- A Vuetify icon with no click listener is hidden from assistive technology -->
      <span class="d-sr-only">{{ errorLabel }}</span>
    </span>
    <span v-else-if="loading">
      {{ loadingLabel }}
    </span>
    <span v-else>-</span>
  </div>
</template>
<script>
import {getLastActivityTime} from '../js/lastActivity.js';

export default {
  props: {
    space: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    spaceLastActivityTime: 0,
    loading: true,
    failed: false,
    dateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    },
    fullDateFormat: {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
  }),
  computed: {
    loadingLabel() {
      return this.$te('social.spaces.administration.manageSpaces.loadingLastActivity') ?
        this.$t('social.spaces.administration.manageSpaces.loadingLastActivity')
        : '';
    },
    errorLabel() {
      return this.$t('social.spaces.administration.manageSpaces.lastActivityLoadFailed');
    },
  },
  created() {
    this.getLastSpaceActivity();
  },
  methods: {
    async getLastSpaceActivity() {
      this.loading = true;
      this.failed = false;
      try {
        // null when the viewer may not read it: displayed as no activity
        this.spaceLastActivityTime = await getLastActivityTime(this.space, this.$root) || 0;
      } catch (e) {
        // A failure is not displayed as no activity
        this.failed = true;
      } finally {
        window.setTimeout(() => this.loading = false, 50);
      }
    },
  },
};
</script>
