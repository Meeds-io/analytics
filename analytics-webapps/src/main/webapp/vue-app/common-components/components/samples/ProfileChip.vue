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
  <a
    v-if="displayName"
    :title="displayName"
    :href="url"
    class="text-truncate"
    rel="nofollow"
    target="_blank">
    {{ displayName }}
  </a>
  <code v-else>
    {{ spaceId || identityId }}
  </code>
</template>

<script>

export default {
  props: {
    identity: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  computed: {
    identityId() {
      return this.identity && this.identity.identityId;
    },
    remoteId() {
      return this.identity && this.identity.remoteId;
    },
    providerId() {
      return this.identity && this.identity.providerId;
    },
    avatar() {
      return this.identity && this.identity.avatar;
    },
    spaceId() {
      return this.identity && this.identity.spaceId;
    },
    displayName() {
      return this.identity && this.identity.displayName;
    },
    url() {
      if (this.providerId === 'organization') {
        return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.remoteId}`;
      } else if (this.providerId === 'space') {
        return `${eXo.env.portal.context}/g/:spaces:${this.remoteId}/`;
      }
      return '';
    },
  },
};
</script>
