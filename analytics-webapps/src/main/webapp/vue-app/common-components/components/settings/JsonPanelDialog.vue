<!--
  This file is part of the Meeds project (https://meeds.io/).
  Copyright (C) 2022 Meeds Association
  contact@meeds.io
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
  <v-dialog
    id="jsonChartSettingsModal"
    v-model="dialog"
    content-class="uiPopup with-overflow"
    class="editChatSettings"
    width="750px"
    max-width="100vw"
    min-height="450px"
    max-height="100%"
    persistent
    @keydown.esc="dialog = false">
    <v-card class="elevation-12">
      <div class="ignore-vuetify-classes popupHeader ClearFix">
        <a
          class="uiIconClose pull-right"
          aria-hidden="true"
          @click="dialog = false">
        </a>
        <span class="ignore-vuetify-classes PopupTitle popupTitle">
          JSON Panel of chart <span class="primary--text">{{ settings && $t(settings.title) }}</span>
        </span>
      </div>
      <v-card-text>
        <v-textarea
          v-model="settingJsonContent"
          :row-height="15"
          class="jsonSetting"
          readonly />
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <button class="btn ignore-vuetify-classes" @click="dialog = false">
          Close
        </button>
        <v-spacer />
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  data() {
    return {
      dialog: false,
    };
  },
  computed: {
    chartSettings() {
      return Object.assign({}, this.settings);
    },
    settingJsonContent() {
      return this.settings && JSON.stringify(this.chartSettings, null, 2);
    },
  },
  methods: {
    open() {
      this.dialog = true;
    },
  },
};
</script>
