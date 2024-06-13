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
  <v-flex class="analytics-percentage-chart-body align-center border-box-sizing">
    <h1
      v-if="initialized"
      :style="`color: ${chartColor};`"
      class="font-weight-bold display-2">
      {{ currentPeriodPercentage }}%
    </h1>
    <template v-if="isUserLimit">
      <v-tooltip bottom>
        <template #activator="{ on, attrs }">
          <div
            class="text-sub-title text-no-wrap mt-2"
            v-bind="attrs"
            v-on="on">
            {{ $t('analytics.ofActiveUsers') }}
            <span class="primary--text">{{ $t('analytics.percentOfUsers', {0: percentage}) }}</span>
          </div>
        </template>
        <ul class="ps-0">
          <li>- {{ $t('analytics.currentPeriodUsers', {0: currentPeriodLimit}) }}</li>
          <li>- {{ $t('analytics.previousPeriodUsers', {0: lastPeriodLimit}) }}</li>
        </ul>
      </v-tooltip>
    </template>
    <v-tooltip bottom>
      <template #activator="{ on, attrs }">
        <div
          class="text-no-wrap mt-1"
          v-bind="attrs"
          v-on="on">
          <span :class="lastPeriodComparaisonClass">
            {{ $t('analytics.points', {0: diffSign, 1: diffWithLastPeriod}) }}
          </span>
          <span class="text-sub-title">
            {{ $t('analytics.vsLastPeriod') }}
          </span>
        </div>
      </template>
      <span>{{ $t('analytics.previousPeriodValue', {0: `${lastPeriodPercentage}%`}) }}</span>
    </v-tooltip>
  </v-flex>
</template>
<script>
export default {
  props: {
    settings: {
      type: Object,
      default: null,
    },
  },
  data () {
    return {
      initialized: false,
      lastPeriodValue: 0,
      currentPeriodValue: 0,
      lastPeriodThreshold: 0,
      currentPeriodThreshold: 0,
      lastPeriodLimit: 0,
      currentPeriodLimit: 0,
    };
  },
  computed: {
    hasLimit() {
      return this.settings && this.settings.percentageLimit;
    },
    isUserLimit() {
      return this.hasLimit && this.settings.percentageLimit.field === 'userId';
    },
    percentage() {
      return this.hasLimit && this.settings.percentageLimit.percentage;
    },
    chartColor() {
      return this.settings && this.settings.colors && this.settings.colors.length && this.settings.colors[0] || '#319ab3';
    },
    currentPeriodPercentage() {
      const currentPeriodPercentage = this.currentPeriodThreshold
             && (Math.round((this.currentPeriodValue / this.currentPeriodThreshold) * 10000) / 100)
              || 0;
      return this.$analyticsUtils.toFixed(currentPeriodPercentage);
    },
    lastPeriodPercentage() {
      const lastPeriodPercentage = this.lastPeriodThreshold
            && (Math.round((this.lastPeriodValue / this.lastPeriodThreshold) * 10000) / 100)
            || 0;
      return this.$analyticsUtils.toFixed(lastPeriodPercentage);
    },
    diffSign() {
      if (this.currentPeriodPercentage === this.lastPeriodPercentage) {
        return '';
      } else if (this.currentPeriodPercentage > this.lastPeriodPercentage) {
        return '+';
      } else {
        return '-';
      }
    },
    diffWithLastPeriod() {
      const diffWithLastPeriod = Math.abs(this.currentPeriodPercentage - this.lastPeriodPercentage);
      return this.$analyticsUtils.toFixed(diffWithLastPeriod);
    },
    lastPeriodComparaisonClass() {
      if (this.currentPeriodPercentage === this.lastPeriodPercentage) {
        return '';
      } else if (this.currentPeriodPercentage > this.lastPeriodPercentage) {
        return 'success--text';
      } else {
        return 'error--text';
      }
    },
    progressBarValueClass(){
      return (this.currentPeriodPercentage > 9 ? this.currentPeriodPercentage : 10) / 2;
    }
  },
  watch: {
    data() {
      this.init();
    }
  },
  methods: {
    init(chartsData) {
      if (chartsData){
        this.currentPeriodValue = chartsData.currentPeriodValue;
        this.currentPeriodThreshold = chartsData.currentPeriodThreshold;
        this.lastPeriodValue = chartsData.previousPeriodValue;
        this.lastPeriodThreshold = chartsData.previousPeriodThreshold;
        this.currentPeriodLimit = chartsData.currentPeriodLimit || 0;
        this.lastPeriodLimit = chartsData.previousPeriodLimit || 0;
      }
      this.$nextTick().then(() => this.initialized = true);
    },
  },
};
</script>