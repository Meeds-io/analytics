<!--

 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io

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
  <help-drawer
    title-key="analytics.collaborativeActivity.help.title"
    @closed="$emit('closed')">
    <!-- Sizes and gaps are the design's: 16px texts in the text colour, 12px
         sentences in the sub-title grey, 28px before the heading, 20px before
         the groups, 18px between groups, 24px around the divider -->
    <div slot="content" class="analytics-collaborative-activity-help px-3 pt-2 text-color">
      <p class="text-subtitle-1 mb-7">{{ $t('analytics.collaborativeActivity.help.intro') }}</p>
      <h4 class="text-subtitle-1 font-weight-bold mb-5">
        {{ $t('analytics.collaborativeActivity.help.counted') }}
      </h4>
      <v-progress-circular
        v-if="loading"
        class="my-2"
        color="primary"
        size="24"
        indeterminate />
      <p v-else-if="failed" class="error--text">
        {{ $t('analytics.collaborativeActivity.help.loadFailed') }}
      </p>
      <template v-else>
        <div
          v-for="group in groups"
          :key="group.title"
          class="analytics-collaborative-activity-help-group"
          style="margin-bottom: 18px">
          <div class="analytics-collaborative-activity-help-group-title text-subtitle-1">{{ group.title }}</div>
          <div
            v-for="(sentence, index) in group.counted"
            :key="index"
            class="analytics-collaborative-activity-help-group-action text-caption text-sub-title">
            {{ sentence }}
          </div>
        </div>
      </template>
      <v-divider class="my-6" />
      <h4 class="text-subtitle-1 font-weight-bold mb-4">
        {{ $t('analytics.collaborativeActivity.help.goodToKnow') }}
      </h4>
      <p class="text-subtitle-1 mb-3">- {{ $t('analytics.collaborativeActivity.help.goodToKnow.oneAction') }}</p>
      <p class="text-subtitle-1 mb-0">- {{ $t('analytics.collaborativeActivity.help.goodToKnow.installedOnly') }}</p>
    </div>
  </help-drawer>
</template>
<script>
export default {
  props: {
    /* [{title, counted: [sentence]}], already translated and ordered */
    groups: {
      type: Array,
      default: () => [],
    },
    loading: {
      type: Boolean,
      default: false,
    },
    failed: {
      type: Boolean,
      default: false,
    },
  },
};
</script>
