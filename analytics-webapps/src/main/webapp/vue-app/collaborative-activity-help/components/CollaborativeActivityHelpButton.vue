<!--

 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io

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
  <span class="d-inline-flex align-center">
    <v-btn
      ref="button"
      :title="tooltip"
      :aria-label="tooltip"
      class="analytics-collaborative-activity-help-button"
      icon
      small
      @click.stop="open">
      <v-icon size="16">fa-question-circle</v-icon>
    </v-btn>
    <analytics-collaborative-activity-help-drawer
      v-if="drawer"
      :groups="groups"
      :loading="loading"
      :failed="failed"
      @closed="close" />
  </span>
</template>
<script>
export default {
  props: {
    /* The i18n key of the tooltip, which names the figure the help explains
       ("Learn more about Last Activity"): the drawer itself names no screen */
    tooltipKey: {
      type: String,
      required: true,
    },
  },
  data: () => ({
    drawer: false,
    groups: null,
    loading: false,
    failed: false,
  }),
  computed: {
    tooltip() {
      return this.$t(this.tooltipKey);
    },
  },
  methods: {
    open() {
      this.drawer = true;
      if (!this.groups) {
        this.loadGroups();
      }
    },
    close() {
      this.drawer = false;
      // The focus goes back to the control that opened the drawer
      this.$nextTick(() => this.$refs.button?.$el?.focus?.());
    },
    async loadGroups() {
      this.loading = true;
      this.failed = false;
      try {
        const entries = await this.$analyticsService.getCollaborativeActivityHelp();
        // The endpoint lists the declarations in the order the jars were
        // read, which is not stable: the groups are shown by displayed title
        const collator = new Intl.Collator(eXo.env.portal.language, {sensitivity: 'base'});
        this.groups = (entries || [])
          .map(entry => ({
            title: this.$t(entry.title),
            counted: (entry.counted || []).map(key => this.$t(key)),
          }))
          .sort((a, b) => collator.compare(a.title, b.title));
      } catch (e) {
        // A failed load is not displayed as an empty list
        this.failed = true;
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
