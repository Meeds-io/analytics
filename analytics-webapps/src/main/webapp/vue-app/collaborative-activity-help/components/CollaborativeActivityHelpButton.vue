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
    <!-- A v-btn, so the control gets the platform's focus styles like every
         other button: 24px around the 16px glyph Manage Spaces draws, or the
         size its host names (36px, the icon button's default, beside the
         chart filter's delete button) -->
    <v-btn
      ref="button"
      :title="tooltip"
      :aria-label="tooltip"
      :width="buttonSize || iconSize + 8"
      :height="buttonSize || iconSize + 8"
      icon
      @click.stop="open">
      <v-icon :size="iconSize">fa-question-circle</v-icon>
    </v-btn>
    <analytics-collaborative-activity-help-drawer
      v-if="drawer"
      :groups="groups"
      :failed="failed"
      @closed="close" />
  </span>
</template>
<script>
// The design's order of the help groups, by the addon that owns each title
// key (<addon>.collaborativeActivity.help.title): Posts, Documents,
// News & Notes, Agenda, Tasks, Polls, Chat
const DESIGN_ORDER = ['social', 'ecms', 'content', 'agenda', 'task', 'poll', 'matrix'];

export default {
  props: {
    /* The i18n key of the tooltip, which names the figure the help explains
       ("Learn more about Last Activity"): the drawer itself names no screen */
    tooltipKey: {
      type: String,
      required: true,
    },
    /* The glyph's size in px; the button is 8px wider unless the host sets
       buttonSize, so a design gap measured from the glyph keeps 4px of the
       button on each side */
    iconSize: {
      type: Number,
      default: 16,
    },
    /* The button's size in px; 8px more than the glyph unless the host names
       the size of the buttons it sits beside */
    buttonSize: {
      type: Number,
      default: null,
    },
  },
  data: () => ({
    drawer: false,
    groups: null,
    failed: false,
  }),
  computed: {
    tooltip() {
      return this.$t(this.tooltipKey);
    },
  },
  methods: {
    async open() {
      // The drawer opens complete: the groups are read before it shows, once
      if (!this.groups) {
        await this.loadGroups();
      }
      this.drawer = true;
    },
    close() {
      this.drawer = false;
      // The focus goes back to the control that opened the drawer
      this.$nextTick(() => this.$refs.button?.$el?.focus?.());
    },
    async loadGroups() {
      this.failed = false;
      try {
        const entries = await this.$analyticsService.getCollaborativeActivityHelp();
        // The endpoint lists the declarations in the order the jars were
        // read, which is not stable: the groups follow the design's order,
        // read from the addon that owns each title key, then any other addon
        // by displayed title
        const collator = new Intl.Collator(eXo.env.portal.language, {sensitivity: 'base'});
        const rank = entry => {
          const index = DESIGN_ORDER.indexOf(entry.title?.split('.')[0]);
          return index < 0 ? DESIGN_ORDER.length : index;
        };
        this.groups = (entries || [])
          .map(entry => ({
            rank: rank(entry),
            title: this.$t(entry.title),
            counted: (entry.counted || []).map(key => this.$t(key)),
          }))
          .sort((a, b) => a.rank - b.rank || collator.compare(a.title, b.title));
      } catch (e) {
        // A failed load is not displayed as an empty list
        this.failed = true;
      }
    },
  },
};
</script>
