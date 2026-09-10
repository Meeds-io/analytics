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
  <div class="d-flex flex-column white analytics-compact-period-popup">
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
      One row per bound, aligned on a 3-column grid so both rows' dates and
      times line up. Laid out on a single centered line the six items
      overflowed the menu's fixed 420px and the leading "from" was clipped.
    -->
    <div class="pa-4 analytics-compact-period-times">
      <span class="text-body analytics-period-bound-label">{{ $t('analytics.from') }}</span>
      <input
        v-model="fromDate"
        type="date"
        :max="maxDate"
        :aria-label="$t('analytics.from')"
        class="text-body analytics-date-time-selection analytics-period-bound-date"
        @change="onBoundInput">
      <input
        v-model="fromTime"
        type="time"
        :aria-label="$t('analytics.from')"
        class="text-body analytics-date-time-selection"
        @change="apply">
      <span class="text-body analytics-period-bound-label">{{ $t('analytics.toDate') }}</span>
      <input
        v-model="toDate"
        type="date"
        :max="maxDate"
        :aria-label="$t('analytics.toDate')"
        class="text-body analytics-date-time-selection analytics-period-bound-date"
        @change="onBoundInput">
      <input
        v-model="toTime"
        type="time"
        :aria-label="$t('analytics.toDate')"
        class="text-body analytics-date-time-selection"
        @change="apply">
    </div>
  </div>
</template>
<script>
export default {
  emits: ['change', 'close'],
  props: {
    /**
     * Currently applied period, {min, max, period}. Never mutated: this only
     * emits the next one, the owner decides what to apply.
     */
    period: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    lang: eXo.env.portal.language && eXo.env.portal.language.replace('_', '-'),
    dates: [],
    fromTime: '00:00',
    toTime: '23:59',
    periodName: null,
  }),
  computed: {
    uid() {
      return this._uid;
    },
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
    // Each bound is editable in its own row as well as on the calendar, and
    // both write back into the same `dates` array so the two stay one state
    fromDate: {
      get() {
        return this.dates && this.dates[0] || '';
      },
      set(value) {
        this.$set(this.dates, 0, value);
      },
    },
    toDate: {
      get() {
        return this.dates && (this.dates[1] || this.dates[0]) || '';
      },
      set(value) {
        this.$set(this.dates, 1, value);
      },
    },
  },
  methods: {
    // Reseeds the form from the currently applied period, so reopening never
    // shows a stale range
    init() {
      const from = this.period && new Date(this.period.min);
      const to = this.period && new Date(this.period.max);
      this.dates = from && to ? [this.toIsoDate(from), this.toIsoDate(to)] : [];
      this.fromTime = from ? this.toIsoTime(from) : '00:00';
      this.toTime = to ? this.toIsoTime(to) : '23:59';
      this.periodName = this.period && this.period.period || null;
    },
    // Emits the period the form currently describes
    apply() {
      if (!this.dates || !this.dates.length || !this.dates[0]) {
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
    onDatesInput() {
      this.periodName = null;
      this.fromTime = '00:00';
      this.toTime = '23:59';
      // A range picker emits the first click as a one-element array: that is
      // a half-picked range, not a period. Emitting it made the owner reload
      // on the opening click and re-render the menu under the pointer, so
      // the second click never landed and no range could be picked. Closing
      // the popup still commits a single date through apply().
      if (this.dates && this.dates.length > 1) {
        this.apply();
      }
    },
    // A bound typed in its own row is no longer one of the named shortcuts
    onBoundInput() {
      this.periodName = null;
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
      this.$emit('close');
    },
    toIsoDate(date) {
      const pad = n => `${n}`.padStart(2, '0');
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    },
    toIsoTime(date) {
      const pad = n => `${n}`.padStart(2, '0');
      return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    },
  },
};
</script>
