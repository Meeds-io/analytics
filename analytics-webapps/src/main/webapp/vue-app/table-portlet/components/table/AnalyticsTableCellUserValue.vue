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
  <v-progress-circular
    v-if="loading"
    size="24"
    color="primary"
    indeterminate />
  <v-tooltip v-else-if="error" bottom>
    <template #activator="{ on, attrs }">
      <i
        class="uiIconColorError"
        v-bind="attrs"
        v-on="on"></i>
    </template>
    <span>{{ $t('analytics.errorRetrievingDataForValue', {0: value}) }}</span>
  </v-tooltip>
  <exo-user-avatar
    v-else-if="user"
    :identity="user"
    :size="32"
    extra-class="analytics-table-user my-1"
    avatar-class="border-color"
    popover>
    <template v-if="user.deleted === 'true'" slot="subTitle">
      <v-chip x-small disabled>
        {{ $t('analytics.deletedUser') }}
      </v-chip>
    </template>
  </exo-user-avatar>
  <v-tooltip v-else bottom>
    <template #activator="{ on, attrs }">
      <i
        class="uiIconColorInfo"
        v-bind="attrs"
        v-on="on"></i>
    </template>
    <span>{{ $t('analytics.errorRetrievingIdentityWithId', {0: value}) }}</span>
  </v-tooltip>
</template>

<script>
export default {
  props: {
    value: {
      type: Object,
      default: function() {
        return null;
      },
    },
    item: {
      type: Object,
      default: function() {
        return null;
      },
    },
    labels: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    loading: true,
    error: false,
    user: null,
  }),
  created() {
    if (this.value && this.value.username) {
      this.user = this.value;
      this.loading = false;
    } else if (this.value && Number(this.value)) {
      this.loading = true;
      this.error = false;
      this.$identityService.getIdentityById(this.value)
        .then(identity => {
          this.item.identity = this.user = identity && identity.profile;
          this.$forceUpdate();
        })
        .catch(() => this.error = true)
        .finally(() => this.loading = false);
    } else {
      this.loading = false;
    }
  },
};
</script>