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
  <div
    class="analytics-chart-period-selector"
    :class="{ 'analytics-chart-period-selector-compact': compact }">
    <!--
      The shared select-period widget (Meeds-io/social) is not used:
      it fails to mount at all in some states (confirmed via console
      RangeError inside its date-picker header) and separately
      recomputes its calendar from toLocaleDateString() on reopen,
      which the picker can't parse. A self-contained menu avoids both,
      feeding the date-picker plain ISO strings and computing period
      shortcuts directly. It doubles as the full-width display (when
      there is room) and the icon-only compact activator.
    -->
    <v-menu
      v-model="menu"
      :close-on-content-click="false"
      :attach="attach"
      min-width="420"
      max-width="420"
      offset-y>
      <template #activator="{ on }">
        <span class="d-flex align-center" style="min-width: 0;">
          <v-btn
            v-if="compact"
            ref="activator"
            icon
            :aria-label="$t('analytics.selectPeriod')"
            v-on="on"
            @click="initPanel">
            <v-icon size="18">fa-calendar-alt</v-icon>
          </v-btn>
          <button
            v-else
            ref="activator"
            type="button"
            :aria-label="$t('analytics.selectPeriod')"
            :title="$t('analytics.selectPeriod')"
            class="analytics-period-selector-full d-flex align-center"
            v-on="on"
            @click="initPanel">
            <v-icon size="16" class="me-2">fa-calendar-alt</v-icon>
            <span class="text-truncate">{{ rangeLabel }}</span>
          </button>
        </span>
      </template>
      <div ref="popup">
        <analytics-period-picker-panel
          ref="panel"
          :period="period"
          @change="$emit('change', $event)"
          @close="menu = false" />
      </div>
    </v-menu>
  </div>
</template>
<script>
export default {
  emits: ['change'],
  props: {
    /**
     * Currently applied period, {min, max, period}: the panel is seeded from
     * it every time the menu is opened.
     */
    period: {
      type: Object,
      default: null,
    },
    /**
     * Icon-only activator, for a header too narrow to show the range.
     */
    compact: {
      type: Boolean,
      default: false,
    },
    /**
     * Renders the popup inside this component rather than letting Vuetify
     * teleport it to the shared VuetifyApp root. Required inside a drawer,
     * where a teleported overlay lands behind it (same reason the settings
     * drawers pass `attach` to their own selects).
     */
    attach: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    menu: false,
    lang: eXo.env.portal.language && eXo.env.portal.language.replace('_', '-'),
  }),
  computed: {
    rangeLabel() {
      if (!this.period) {
        return '';
      }
      return this.$t('analytics.period.range', {
        0: this.formatDate(new Date(this.period.min)),
        1: this.formatDate(new Date(this.period.max)),
      });
    },
  },
  mounted() {
    // v-menu's own close-on-click stopped closing the popup in practice
    // (unclear why - possibly a leaked document click listener from an
    // unrelated component), so it's handled explicitly here instead of
    // relying on Vuetify's built-in outside-click detection.
    document.addEventListener('click', this.handleOutsideClick, true);
  },
  beforeDestroy() {
    document.removeEventListener('click', this.handleOutsideClick, true);
  },
  methods: {
    initPanel() {
      this.$nextTick().then(() => this.$refs.panel && this.$refs.panel.init());
    },
    handleOutsideClick(event) {
      if (!this.menu) {
        return;
      }
      const popup = this.$refs.popup;
      const activator = this.$refs.activator && (this.$refs.activator.$el || this.$refs.activator);
      if ((popup && popup.contains(event.target)) || (activator && activator.contains(event.target))) {
        return;
      }
      // Closing commits what was typed, the same as changing a value does
      if (this.$refs.panel) {
        this.$refs.panel.apply();
      }
      this.menu = false;
    },
    formatDate(date) {
      // Explicit locale + options (unlike the shared select-period widget's
      // bare toLocaleDateString() call): safe for display, never fed back
      // into a date input, so it doesn't need to stay ISO-parseable.
      return date.toLocaleDateString(this.lang, {day: 'numeric', month: 'short', year: 'numeric'});
    },
  },
};
</script>
