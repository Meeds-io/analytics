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
      <!-- US05 design: in the profile-mode drawer the space name is bold in
           the primary color; the theme helpers do it without portlet CSS -->
      <v-list-item-title
        :class="emphasized ? 'primary--text font-weight-bold' : 'text-color'"
        class="text-truncate-2 text-wrap spaceTitle">
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
    providedSpace: {
      type: Object,
      default: () => null,
    },
    emphasized: {
      type: Boolean,
      default: false,
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
      // Every listed space is a link, member or not: a non-member lands on the
      // space access page, which offers to join, to request to join, or says
      // the space is invite-only depending on its registration. Hidden spaces
      // the viewer is not in are never listed. Source: review round 1 of
      // Meeds-io/analytics#446 (eXIP 7.3.0.18 rationale, "discover other
      // spaces they can access"); PO confirmation pending on board story US02
      // (task 89466), whose validated rendering predates this link.
      return this.space?.id && `${eXo.env.portal.context}/s/${this.space.id}` || null;
    },
  },
  created() {
    if (this.providedSpace) {
      this.space = this.providedSpace;
      return;
    }
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
