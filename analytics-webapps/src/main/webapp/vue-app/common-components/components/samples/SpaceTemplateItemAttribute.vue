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
    {{ spaceTemplateName }}
    <v-chip>{{ attrValue }}</v-chip>
  </div>
</template>
<script>
export default {
  props: {
    attrKey: {
      type: Object,
      default: null,
    },
    attrValue: {
      type: Object,
      default: null,
    },
    options: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    spaceTemplateName: null,
  }),
  created() {
    this.init();
  },
  methods: {
    async init() {
      const translations = await fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/social/translations/spaceTemplate/${this.attrValue}/name`, {
        method: 'GET',
        credentials: 'include',
      }).then(resp => resp?.json?.());
      this.spaceTemplateName = translations?.[eXo.env.portal.language]
        || translations?.[eXo.env.portal.defaultLanguage]
        || translations?.['en'];
    }
  }
};
</script>