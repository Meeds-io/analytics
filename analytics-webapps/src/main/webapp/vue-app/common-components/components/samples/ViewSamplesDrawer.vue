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
  <exo-drawer
    ref="samplesDrawer"
    right
    body-classes="hide-scroll"
    class="samplesDrawer"
    @closed="$emit('cancel')">
    <template slot="title">
      {{ title }}
    </template>
    <template slot="titleIcons">
      <v-btn
        :disabled="loading"
        icon
        color="primary"
        class="mx-2"
        @click="refresh">
        <v-icon>
          refresh
        </v-icon>
      </v-btn>
    </template>
    <template slot="content">
      <v-row justify="center" class="ma-0 analyticsDrawerContent">
        <v-expansion-panels v-if="chartDatas" accordion>
          <analytics-sample-item
            v-for="chartData in chartDatas"
            :key="chartData.id"
            :chart-data="chartData"
            :users="users"
            :spaces="spaces" />
        </v-expansion-panels>
      </v-row>
    </template>
    <template v-if="canLoadMore" slot="footer">
      <div class="d-flex">
        <v-btn
          :disabled="loading"
          color="primary"
          class="ma-auto"
          text
          @click="loadMore">
          Load More
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props: {
    selectedPeriod: {
      type: Object,
      default: function() {
        return null;
      },
    },
    title: {
      type: String,
      default: function() {
        return null;
      },
    },
    users: {
      type: Object,
      default: function() {
        return null;
      },
    },
    spaces: {
      type: Object,
      default: function() {
        return null;
      },
    },
    retrieveSamplesUrl: {
      type: String,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    loading: false,
    chartDatas: null,
    pageSize: 10,
    limit: 10,
    canLoadMore: false,
  }),
  watch: {
    loading() {
      if (this.loading) {
        this.$refs.samplesDrawer.startLoading();
      } else {
        this.$refs.samplesDrawer.endLoading();
        this.computeCanLoadMore();
      }
    },
  },
  methods: {
    open() {
      this.$refs.samplesDrawer.open();
      this.$nextTick().then(this.refresh);
    },
    loadMore() {
      this.limit += this.pageSize;
      this.loadData();
    },
    refresh() {
      this.loadData();
    },
    loadData() {
      if (!this.selectedPeriod) {
        return;
      }

      let loadedChartData;
      const params = {
        lang: eXo.env.portal.language && eXo.env.portal.language.replace('_','-'),
        min: this.selectedPeriod.min,
        max: this.selectedPeriod.max + 60000,
        timeZone: this.$analyticsUtils.USER_TIMEZONE_ID,
        limit: this.limit,
      };

      this.loading = true;
      return fetch(this.retrieveSamplesUrl, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'pragma': 'no-cache',
          'cache-control': 'no-cache',
        },
        body: $.param(params),
      })
        .then((resp) => {
          if (resp && resp.ok) {
            return resp.json();
          } else {
            throw new Error('Error getting analytics samples with filters:', params);
          }
        })
        .then((chartDatas) => loadedChartData = chartDatas)
        .then((chartDatas) => this.loadUsersAndSpacesObjects(chartDatas))
        .then(() => this.chartDatas = [])
        .then(() => this.$nextTick())
        .then(() => this.chartDatas = loadedChartData)
        .catch((e) => {
          console.error('fetch analytics - error', e);
          this.error = 'Error getting analytics';
        })
        .finally(() => this.loading = false);
    },
    loadUsersAndSpacesObjects(chartDatas, index) {
      index = index || 0;
      if (!chartDatas || index >= chartDatas.length) {
        return;
      }
      const chartData = chartDatas[index];
      if (chartData) {
        return this.$analyticsUtils.loadUser(this.users, chartData.userId)
          .then(() => this.$analyticsUtils.loadUser(this.users, chartData.parameters && chartData.parameters.modifierSocialId))
          .then(() => this.$analyticsUtils.loadSpace(this.spaces, chartData.spaceId))
          .then(() => this.loadUsersAndSpacesObjects(chartDatas, ++index));
      }
    },
    computeCanLoadMore() {
      if (this.chartDatas) {
        const loadedDataLength = Object.keys(this.chartDatas).length;
        this.canLoadMore = loadedDataLength >= this.limit;
      } else {
        this.canLoadMore = false;
      }
    },
  }
};
</script>
