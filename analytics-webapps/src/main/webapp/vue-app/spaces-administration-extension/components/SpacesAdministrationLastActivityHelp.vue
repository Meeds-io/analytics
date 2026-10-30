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
  <!-- The design puts 12px between the label and the 16px glyph, and 12px
       between the glyph and the sort arrow; the 28px button leaves 6px
       around its glyph, and Vuetify's arrow brings its own 4px -->
  <analytics-collaborative-activity-help-button
    tooltip-key="analytics.collaborativeActivity.help.tooltip.lastActivity"
    style="margin-inline-start: 6px; margin-inline-end: 2px" />
</template>
<script>
export default {
  props: {
    /* The column header Manage Spaces renders this component in */
    header: {
      type: Object,
      default: null,
    },
  },
};
</script>
