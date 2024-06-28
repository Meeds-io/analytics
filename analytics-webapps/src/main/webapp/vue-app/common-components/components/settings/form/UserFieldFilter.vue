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
      ref="userSuggester"
      v-model="selectedUser"
      :search-options="searchOptions"
      :labels="suggesterLabels"
      :ignore-items="userIds"
      include-users
      class="analytics-suggester"
      sugester-class="my-0"
      @input="selectIdentity" />
    <div v-if="users && multipleOperator" class="d-flex flex-column">
      <div
        v-for="user in users"
        :key="user">
        <v-chip
          :title="user.profile.fullName"
          color="primary"
          close
          class="identitySuggesterItem mt-2"
          @click:close="remove(user)">
          <v-avatar left>
            <v-img :src="user.profile.avatarUrl" />
          </v-avatar>
          <span class="text-truncate">
            {{ user.profile.fullName }}
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
    selectedUser: null,
    users: [],
    searchOptions: {
      currentUser: '',
    },
  }),
  computed: {
    operatorType() {
      return this.filter && this.filter.type;
    },
    multipleOperator() {
      return this.operatorType === 'IN_SET' || this.operatorType === 'NOT_IN_SET';
    },
    userIds() {
      return this.users.map(user => user.id);
    },
  },
  created() {
    if (this.filter.valueString) {
      const identityIds = this.filter.valueString.split(',');
      const promises = [];
      const selectedUsers = [];
      identityIds.forEach(identityId => {
        const promise = this.$identityService.getIdentityById(identityId.trim())
          .then(identity => {
            if (identity && identity.profile) {
              const selectedUser = {
                id: `${identity.providerId}:${identity.remoteId}`,
                providerId: identity.providerId,
                remoteId: identity.remoteId,
                identityId: identity.id,
                profile: {
                  fullName: identity.profile.fullname,
                  avatarUrl: identity.profile.avatar,
                }
              };
              selectedUsers.push(selectedUser);
            }
          })
          .catch(e => console.error('Error retrieving user with identity id', identityId, e));
        promises.push(promise);
      });
      Promise.all(promises).finally(() => {
        if (this.multipleOperator) {
          this.users = selectedUsers;
        } else {
          this.selectedUser = selectedUsers && selectedUsers[0] || null;
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
        } else if (this.userIds.includes(selectedIdentityId)) {
          this.selectedUser = null;
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
          if (identity.identityId) {
            identityIds.push(identity.identityId);
            if (this.multipleOperator) {
              this.users.push(identity);
            }
          } else {
            const promise = this.$identityService.getIdentityByProviderIdAndRemoteId(identity.providerId, identity.remoteId)
              .then(identity => {
                if (identity.profile) {
                  identityIds.push(identity.id);
                  if (this.multipleOperator) {
                    this.users.push({
                      id: `${identity.providerId}:${identity.remoteId}`,
                      providerId: identity.providerId,
                      remoteId: identity.remoteId,
                      identityId: identity.id,
                      profile: {
                        fullName: identity.profile.fullname,
                        avatarUrl: identity.profile.avatar,
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
            this.selectedUser = null;
          }
        });
      }
    },
    remove(user) {
      if (user && this.users.indexOf(user) >= 0) {
        this.users.splice(this.users.indexOf(user), 1);
        this.filter.valueString = this.users.map(user => user.identityId || user.id).join(',');
      }
    },
  },
};
</script>