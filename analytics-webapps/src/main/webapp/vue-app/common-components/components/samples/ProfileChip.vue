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
  <v-progress-circular
    v-if="loading"
    indeterminate
    color="primary" />
  <div v-else class="d-flex flex-row">
    <template v-if="identity">
      <exo-space-avatar
        v-if="isSpace"
        :space="identity"
        extra-class="flex-grow-1"
        size="24"
        popover
        link-style />
      <exo-user-avatar
        v-else
        :identity="identityProfile"
        class="flex-grow-1"
        size="24"
        popover
        link-style />
      <code v-if="displayValue">
        ({{ profileId }})
      </code>
    </template>
    <code v-else>
      {{ profileId }}
    </code>
  </div>
</template>

<script>

export default {
  props: {
    profileId: {
      type: String,
      default: null,
    },
    isSpace: {
      type: Boolean,
      default: false,
    },
    displayValue: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    identity: null,
    loading: true,
  }),
  computed: {
    identityProfile() {
      return !this.isSpace && this.identity?.profile;
    },
  },
  created() {
    if (this.isSpace) {
      this.$analyticsUtils.loadSpace(this.$root.spaces, this.profileId)
        .then(identity => this.identity = identity)
        .finally(() => this.loading = false);
    } else {
      this.$analyticsUtils.loadUser(this.$root.users, this.profileId)
        .then(identity => this.identity = identity)
        .finally(() => this.loading = false);
    }
  },
};
</script>
