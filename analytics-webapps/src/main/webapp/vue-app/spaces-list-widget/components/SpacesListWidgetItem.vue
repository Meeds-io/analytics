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
  <v-list-item :href="url" class="pa-1 pb-1">
    <v-list-item-avatar
      :href="url"
      class="my-0"
      tile>
      <v-avatar :size="avatarSize" tile>
        <v-img
          :src="avatarUrl"
          :height="avatarSize"
          :width="avatarSize"
          :max-height="avatarSize"
          :max-width="avatarSize"
          class="mx-auto spaceAvatar" />
      </v-avatar>
    </v-list-item-avatar>
    <v-list-item-content
      :id="id"
      class="pa-0">
      <v-list-item-title class="text-color text-truncate spaceTitle">
        {{ displayName }}
      </v-list-item-title>
    </v-list-item-content>
  </v-list-item>
</template>
<script>
export default {
  props: {
    spaceId: {
      type: String,
      default: () => null,
    },
  },
  data: () => ({
    space: null,
    loading: false,
    avatarSize: 37,
  }),
  computed: {
    avatarUrl() {
      return !this.loading && (this.space?.avatarUrl || `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/spaces/default-image/avatar`);
    },
    displayName() {
      return !this.loading && (this.space?.displayName || this.$t('analytics.spacesListWidget.hiddenSpace'));
    },
    url() {
      if (this.space?.groupId) {
        const uri = this.space.groupId.replace(/\//g, ':');
        return `${eXo.env.portal.context}/g/${uri}/`;
      }
      return null;
    },
  },
  created() {
    this.loading = true;
    this.$spaceService.getSpaceById(this.spaceId)
      .then(space => this.space = space)
      .catch(() => this.space = {
        id: this.spaceId,
      })
      .finally(() => this.loading = false);
  },
};
</script>
