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
    id="drawerPageAnalytics"
    ref="analyticsPage"
    right>
    <template slot="title">
      {{ $t('analytics.analyticsReports') }}
    </template>
    <template slot="content">
      <v-treeview
        :items="pages"
        item-children="children"
        item-key="uri"
        item-text="label"
        class="treeViewPage"
        open-all
        dense>
        <template slot="label" slot-scope="{ item }">
          <a
            v-if="item.link"
            class="text-color"
            :href="item.link">
            {{ item.label }}
          </a>
          <template v-else>
            {{ item.label }}
          </template>
        </template>
      </v-treeview>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    pages: {
      type: Object,
      default: () => [],
    },
  },
  methods: {
    open(){
      this.$refs.analyticsPage.open();
    },
    close(){
      this.$refs.analyticsPage.close();
    },
  }
};
</script>