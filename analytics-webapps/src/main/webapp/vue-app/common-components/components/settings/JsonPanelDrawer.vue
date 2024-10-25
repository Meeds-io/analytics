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
    ref="chartSettingDrawer"
    id="jsonChartSettingsModal"
    v-model="drawer"
    class="editChatSettings"
    allow-expand
    right>
    <template #title>
      {{ $t('analytics.jsonSettings.drawer.title') }}
    </template>
    <template #content>
      <div class="pa-4 fill-height">
        <textarea
          v-model="settingJsonContent"
          class="full-width full-height"
          auto-grow></textarea>
      </div>
    </template>
    <template #footer>
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn me-2"
          elevation="0"
          @click="drawer = false">
          {{ $t('analytics.cancel') }}
        </v-btn>
        <v-btn
          :disabled="!isValidJSON"
          class="btn-primary"
          color="primary"
          elevation="0"
          @click="save">
          {{ $t('analytics.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    settings: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    drawer: false,
    settingJsonContent: null,
  }),
  computed: {
    settingsJSON() {
      try {
        return JSON.parse(this.settingJsonContent);
      } catch (e) {
        return null;
      }
    },
    isValidJSON() {
      return !!this.settingsJSON;
    },
  },
  methods: {
    open() {
      this.drawer = true;
      this.settingJsonContent = this.settings && JSON.stringify(this.settings, null, 2) || '{}';
    },
    save() {
      if (!this.isValidJSON) {
        return;
      }
      this.$emit('save', this.settingsJSON);
      this.drawer = false;
    },
  },
};
</script>
