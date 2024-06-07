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
  <v-flex class="d-flex flex-column">
    <exo-identity-suggester
      ref="spaceSuggester"
      v-model="selectedSpace"
      :labels="suggesterLabels"
      :ignore-items="spaceIds"
      include-spaces
      class="analytics-suggester"
      @input="selectIdentity" />
    <div v-if="spaces && multipleOperator" class="d-flex flex-column">
      <div
        v-for="space in spaces"
        :key="space">
        <v-chip
          :title="space.profile.fullName"
          color="primary"
          close
          class="identitySuggesterItem mt-2"
          @click:close="remove(space)">
          <v-avatar left>
            <v-img :src="space.profile.avatarUrl" />
          </v-avatar>
          <span class="text-truncate">
            {{ space.profile.fullName }}
          </span>
        </v-chip>
      </div>
    </div>
  </v-flex>
</template>

<script>
export default {
  props: {
    filter: {
      type: Object,
      default: function() {
        return null;
      },
    },
    suggesterLabels: {
      type: Array,
      default: function() {
        return [];
      },
    },
  },
  data: () => ({
    selectedSpace: null,
    spaces: [],
  }),
  computed: {
    operatorType() {
      return this.filter && this.filter.type;
    },
    multipleOperator() {
      return this.operatorType === 'IN_SET' || this.operatorType === 'NOT_IN_SET';
    },
    spaceIds() {
      return this.spaces.map(space => space.id);
    },
  },
  created() {
    if (this.filter.valueString) {
      const spaceIds = this.filter.valueString.split(',');
      const promises = [];
      const selectedSpaces = [];
      spaceIds.forEach(spaceId => {
        const promise = this.$spaceService.getSpaceById(spaceId)
          .then(space => {
            if (space) {
              const selectedSpace = {
                id: `space:${space.prettyName}`,
                providerId: 'space',
                remoteId: space.prettyName,
                spaceId: space.id,
                profile: {
                  fullName: space.displayName,
                  avatarUrl: space.avatarUrl,
                }
              };
              selectedSpaces.push(selectedSpace);
            }
          })
          .catch(e => console.error('Error retrieving space with id', spaceId, e));
        promises.push(promise);
      });
      Promise.all(promises).finally(() => {
        if (this.multipleOperator) {
          this.spaces = selectedSpaces;
        } else {
          this.selectedSpace = selectedSpaces && selectedSpaces[0] || null;
        }
      });
    }
  },
  methods: {
    selectIdentity(identity) {
      if (this.multipleOperator) {
        const selectedIdentityId = identity && (identity.length && identity[0].id || identity.id);
        if (!selectedIdentityId) {
          return;
        } else if (this.spaceIds.includes(selectedIdentityId)) {
          this.selectedSpace = null;
          return;
        }
      } else {
        this.filter.valueString = '';
      }
      if (identity) {
        const identities = Array.isArray(identity) && identity || [identity];
        const identityIds = this.multipleOperator && this.filter.valueString && this.filter.valueString.split(',') || [];
        const promises = [];
        identities.forEach(identity => {
          if (identity.spaceId) {
            identityIds.push(identity.spaceId);
            if (this.multipleOperator) {
              this.spaces.push(identity);
            }
          } else {
            const promise = this.$identityService.getIdentityByProviderIdAndRemoteId(identity.providerId, identity.remoteId)
              .then(identity => {
                if (identity.space) {
                  identityIds.push(identity.space.id);
                  if (this.multipleOperator) {
                    this.spaces.push({
                      id: `space:${identity.space.prettyName}`,
                      providerId: 'space',
                      remoteId: identity.space.prettyName,
                      spaceId: identity.space.id,
                      profile: {
                        fullName: identity.space.displayName,
                        avatarUrl: identity.space.avatarUrl,
                      }
                    });
                  }
                }
              })
              .catch(e => console.error('Error retrieving identity with id', identity.id, e));
            promises.push(promise);
          }
        });
        Promise.all(promises).finally(() => {
          this.filter.valueString = identityIds.join(',');
          if (this.multipleOperator) {
            this.selectedSpace = null;
          }
        });
      }
    },
    remove(space) {
      if (space && this.spaces.includes(space)) {
        this.spaces.splice(this.spaces.indexOf(space), 1);
        this.filter.valueString = this.spaces.map(space => space.spaceId || space.id).join(',');
      }
    },
  },
};
</script>