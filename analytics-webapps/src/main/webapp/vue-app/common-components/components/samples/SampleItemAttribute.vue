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
  <v-row>
    <v-col>{{ getI18N(value) }}</v-col>
    <v-col v-if="sampleItemExtension && initialized">
      <extension-registry-component
        :component="extendedSampleItemComponent"
        :params="extendedSampleItemComponentParams" />
    </v-col>
    <v-col v-else-if="value === 'userId' && userIdentity" class="text--secondary">
      {{ chartData[value] }}
      (<analytics-profile-chip :identity="userIdentity" />)
    </v-col>
    <v-col v-else-if="value === 'spaceId' && spaceIdentity" class="text--secondary">
      {{ chartData[value] }}
      (<analytics-profile-chip :identity="spaceIdentity" />)
    </v-col>
    <v-col v-else-if="value === 'modifierSocialId' && userModifierIdentity" class="text--secondary">
      {{ chartData[value] }}
      (<analytics-profile-chip :identity="userModifierIdentity" />)
    </v-col>
    <v-col v-else class="text--secondary">
      {{ chartData[value] }}
    </v-col>
  </v-row>
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
    chartData: {
      type: Object,
      default: function() {
        return null;
      },
    },
    sampleItemExtensions: {
      type: Object,
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
    userIdentity: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    initialized: false,
  }),
  computed: {
    extendedSampleItemComponent() {
      return this.sampleItemExtension && {
        componentName: 'sample-item',
        componentOptions: {
          vueComponent: this.sampleItemExtension.vueComponent,
        },
      } || null;
    },
    sampleItemExtension() {
      if (this.sampleItemExtensions) {
        return Object.values(this.sampleItemExtensions)
          .sort((ext1, ext2) => (ext1.rank || 0) - (ext2.rank || 0))
          .find(extension => extension.match && extension.match(this.value)) || null;
      }
      return null;
    },
    extendedSampleItemComponentParams() {
      return this.sampleItemExtension && {
        value: this.chartData[this.value],
        users: this.users,
      } || null;
    },
    userModifierIdentity() {
      if (this.chartData && this.chartData.modifierSocialId && this.users) {
        const userObj = this.users[this.chartData.modifierSocialId];
        if (userObj) {
          return userObj;
        } else {
          return {
            identityId: this.chartData.modifierSocialId,
          };
        }
      } else {
        return null;
      }
    },
    spaceIdentity() {
      if (this.chartData && this.chartData.spaceId && this.spaces) {
        const spaceObj = this.spaces[this.chartData.spaceId];
        if (spaceObj) {
          return spaceObj;
        } else {
          return {
            spaceId: this.chartData.spaceId,
          };
        }
      } else {
        return null;
      }
    },
  },
  created() {
    if (this.sampleItemExtension && this.sampleItemExtension.isUserIdentity) {
      this.$analyticsUtils.loadUser(this.users, parseInt(this.chartData[this.value]))
        .then(() => this.$nextTick())
        .finally(() => {
          this.initialized = true;
        });
    } else {
      this.initialized = true;
    }
  },
  methods: {
    getI18N(label){
      const fieldLabelI18NKey = `analytics.field.label.${label}`;
      const fieldLabelI18NValue = this.$t(fieldLabelI18NKey);
      return  fieldLabelI18NValue === fieldLabelI18NKey ? label : fieldLabelI18NValue;
    }
  },
};
</script>