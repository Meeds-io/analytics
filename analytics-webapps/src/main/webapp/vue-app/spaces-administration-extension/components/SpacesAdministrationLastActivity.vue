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
  <v-tooltip v-if="spaceLastActivityTime" bottom>
    <template #activator="{on, attrs}">
      <div
        v-on="on"
        v-bind="attrs"
        class="pe-4">
        <date-format :value="spaceLastActivityTime" :format="dateFormat" />
      </div>
    </template>
    <date-format :value="spaceLastActivityTime" :format="fullDateFormat" />
  </v-tooltip>
  <v-progress-circular
    v-else-if="loading"
    color="primary"
    size="24"
    indeterminate
    class="pe-4" />
  <span
    v-else
    class="pe-4">-</span>
</template>
<script>
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
  created() {
    this.getLatSpaceActivity();
  },
  methods: {
    async getLatSpaceActivity() {
      this.loading = true;
      try {
        const spaceLastActivities = await this.$analyticsService.getSamples({
          operations: this.$analyticsService.SPACE_ACTIVITY_OPERATIONS,
          fieldName: 'spaceId',
          fieldValues: [this.space.id],
          sortBy: 'timestamp',
          sortDirection: 'desc',
          limit: 1,
        });
        this.spaceLastActivityTime = spaceLastActivities?.[0]?.timestamp || 0;
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
