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
  <div>
    {{ formattedDuration }}
  </div>
</template>

<script>
export default {
  props: {
    attrKey: {
      type: Object,
      default: function() {
        return null;
      },
    },
    attrValue: {
      type: Object,
      default: function() {
        return null;
      },
    },
    options: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  computed: {
    formattedDuration() {
      return this.formatDuration(this.attrValue);
    }
  },
  methods: {
    formatDuration(durationInSeconds) {
      const months = Math.floor(durationInSeconds / (30 * 24 * 60 * 60));
      durationInSeconds %= 30 * 24 * 60 * 60;
      const days = Math.floor(durationInSeconds / (24 * 60 * 60));
      durationInSeconds %= 24 * 60 * 60;
      const minutes = Math.floor(durationInSeconds / 60);
      const seconds = durationInSeconds % 60;

      let formattedString = '';

      if (months > 0) {
        formattedString += `${months} month${months > 1 ? 's' : ''} `;
      }
      if (days > 0) {
        formattedString += `${days} day${days > 1 ? 's' : ''} `;
      }
      if (minutes > 0) {
        formattedString += `${minutes} minute${minutes > 1 ? 's' : ''} `;
      }
      if (seconds > 0 || formattedString === '') {
        formattedString += `${seconds} second${seconds !== 1 ? 's' : ''}`;
      }
      return formattedString.trim();
    }
  }
};
</script>