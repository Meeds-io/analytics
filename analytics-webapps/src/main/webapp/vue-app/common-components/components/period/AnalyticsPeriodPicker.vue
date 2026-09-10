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
            @click="init">
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
            @click="init">
            <v-icon size="16" class="me-2">fa-calendar-alt</v-icon>
            <span class="text-truncate">{{ rangeLabel }}</span>
          </button>
        </span>
      </template>
      <div ref="popup" class="d-flex flex-column white analytics-compact-period-popup">
        <v-date-picker
          v-model="dates"
          :locale="lang"
          :max="maxDate"
          width="100%"
          show-current
          first-day-of-week="1"
          range
          scrollable
          @input="onDatesInput" />
        <v-divider />
        <div class="analytics-compact-period-options">
          <v-btn
            v-for="item in periodOptions"
            :key="item.value"
            text
            small
            @click="selectPeriodItem(item.value)">
            {{ item.text }}
          </v-btn>
        </div>
        <v-divider />
        <!--
          One row per bound, both in text-body: laid out on a single
          centered line the six items overflowed the menu's fixed
          420px, and the leading "from" was the part clipped away.
        -->
        <div class="d-flex flex-column px-4 py-3 analytics-compact-period-times">
          <div class="d-flex align-center">
            <span class="text-body analytics-compact-period-time-label">{{ $t('analytics.from') }}</span>
            <span class="text-body">{{ fromDateLabel }}</span>
            <input
              v-model="fromTime"
              type="time"
              :aria-label="$t('analytics.from')"
              class="text-body analytics-date-time-selection ms-2"
              @change="apply">
          </div>
          <div class="d-flex align-center mt-2">
            <span class="text-body analytics-compact-period-time-label">{{ $t('analytics.toDate') }}</span>
            <span class="text-body">{{ toDateLabel }}</span>
            <input
              v-model="toTime"
              type="time"
              :aria-label="$t('analytics.toDate')"
              class="text-body analytics-date-time-selection ms-2"
              @change="apply">
          </div>
        </div>
      </div>
    </v-menu>
  </div>
</template>
<script>
export default {
  emits: ['change'],
  props: {
    /**
     * Currently applied period, {min, max, period}: the form is seeded from
     * it every time the menu is opened. Never mutated - the applied period
     * is the owner's, this only emits the next one.
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
    dates: [],
    fromTime: '00:00',
    toTime: '23:59',
    periodName: null,
  }),
  computed: {
    periodOptions() {
      return [
        {value: 'thisYear', text: this.$t('analytics.periodOptions.thisYear')},
        {value: 'thisSemester', text: this.$t('analytics.periodOptions.thisSemester')},
        {value: 'thisQuarter', text: this.$t('analytics.periodOptions.thisQuarter')},
        {value: 'thisMonth', text: this.$t('analytics.periodOptions.thisMonth')},
        {value: 'thisWeek', text: this.$t('analytics.periodOptions.thisWeek')},
        {value: 'today', text: this.$t('analytics.periodOptions.today')},
      ];
    },
    maxDate() {
      return this.toIsoDate(new Date());
    },
    fromDateLabel() {
      return this.dates && this.dates[0] && this.formatIsoDate(this.dates[0]) || '';
    },
    toDateLabel() {
      const date = this.dates && (this.dates[1] || this.dates[0]);
      return date && this.formatIsoDate(date) || '';
    },
    rangeLabel() {
      if (!this.period) {
        return '';
      }
      return `${this.formatDate(new Date(this.period.min))}~${this.formatDate(new Date(this.period.max))}`;
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
    // Reseeds the form from the currently applied period, so reopening the
    // menu never shows a stale range
    init() {
      const from = this.period && new Date(this.period.min);
      const to = this.period && new Date(this.period.max);
      this.dates = from && to ? [this.toIsoDate(from), this.toIsoDate(to)] : [];
      this.fromTime = from ? this.toIsoTime(from) : '00:00';
      this.toTime = to ? this.toIsoTime(to) : '23:59';
      this.periodName = this.period && this.period.period || null;
    },
    // Emits the period the form currently describes: closing the popup
    // commits what was typed, the same as changing a value does
    apply() {
      if (!this.dates || !this.dates.length) {
        return;
      }
      let [from, to] = this.dates;
      if (!to) {
        to = from;
      }
      if (new Date(from) > new Date(to)) {
        [from, to] = [to, from];
      }
      this.$emit('change', {
        period: this.periodName,
        min: new Date(`${from}T${this.fromTime || '00:00'}:00`).getTime(),
        max: new Date(`${to}T${this.toTime || '23:59'}:59.999`).getTime(),
      });
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
      this.apply();
      this.menu = false;
    },
    onDatesInput() {
      this.periodName = null;
      this.fromTime = '00:00';
      this.toTime = '23:59';
      this.apply();
    },
    selectPeriodItem(periodName) {
      const range = this.$analyticsUtils.computePeriodDateRange(periodName);
      if (!range) {
        return;
      }
      this.dates = [this.toIsoDate(range.from), this.toIsoDate(range.to)];
      this.fromTime = '00:00';
      this.toTime = '23:59';
      this.periodName = periodName;
      this.apply();
      this.menu = false;
    },
    toIsoDate(date) {
      const pad = n => `${n}`.padStart(2, '0');
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    },
    toIsoTime(date) {
      const pad = n => `${n}`.padStart(2, '0');
      return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    },
    formatDate(date) {
      // Explicit locale + options (unlike the shared select-period widget's
      // bare toLocaleDateString() call): safe for display, never fed back
      // into a date input, so it doesn't need to stay ISO-parseable.
      return date.toLocaleDateString(this.lang, {day: 'numeric', month: 'short', year: 'numeric'});
    },
    formatIsoDate(isoDateString) {
      return this.formatDate(new Date(`${isoDateString}T00:00:00`));
    },
  },
};
</script>
