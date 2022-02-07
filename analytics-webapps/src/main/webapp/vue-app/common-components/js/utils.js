export const USER_TIMEZONE_ID = new window.Intl.DateTimeFormat().resolvedOptions().timeZone;

export function loadUser(users, userId) {
  if (!userId) {
    return Promise.resolve(null);
  }
  if (users[userId]) {
    return Promise.resolve(users[userId]);
  } else {
    return fetch(`/portal/rest/v1/social/identities/${userId}`)
      .then((resp) => {
        if (resp && resp.ok) {
          return resp.json();
        }
      })
      .then((obj) => {
        obj = obj || {};
        const userObject = {
          identityId: userId,
          providerId: obj.globalId && obj.globalId.domain,
          remoteId: obj.globalId && obj.globalId.localId,
          avatar: obj.avatar || '/eXoSkin/skin/images/system/UserAvtDefault.png',
          displayName: obj.profile && obj.profile.fullname,
        };
        users[userId] = userObject;
      });
  }
}

export function loadSpace(spaces, spaceId) {
  if (!spaceId) {
    return Promise.resolve(null);
  }
  if (spaces[spaceId]) {
    return Promise.resolve(spaces[spaceId]);
  } else {
    let spaceDisplayName;
    return fetch(`/portal/rest/v1/social/spaces/${spaceId}`)
      .then((resp) => {
        if (resp && resp.ok) {
          return resp.json();
        }
      })
      .then(obj => {
        if (obj && obj.identity) {
          spaceDisplayName = obj.displayName;
          return fetch(`${obj.identity}`)
            .then((resp) => resp && resp.ok && resp.json());
        }
      })
      .then((obj) => {
        obj = obj || {};
        const spaceObject = {
          identityId: obj.id,
          providerId: obj.globalId && obj.globalId.domain,
          remoteId: obj.globalId && obj.globalId.localId,
          avatar: obj.avatar || '/eXoSkin/skin/images/system/SpaceAvtDefault.png',
          spaceId: spaceId,
          displayName: spaceDisplayName,
        };
        spaces[spaceId] = spaceObject;
      });
  }
}

export function buildPageLinkRecursively(baseUri, nav) {
  if (!nav) {
    return;
  }
  if (nav.uri && nav.pageKey) {
    nav.link = `${baseUri}/${nav.uri}`;
  }
  if (nav.children && nav.children.length) {
    nav.children.forEach(subNav => buildPageLinkRecursively(baseUri, subNav));
  }
}

export function getPage(siteType, siteName, pageName) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/navigations/${siteType}/?siteName=${siteName}&scope=all&visibility=displayed&visibility=system`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error retrieving pages of current site');
    }
    return resp.json();
  }).then(navigations => {
    if (!navigations) {
      return null;
    }
    return getPageRecursively(navigations, pageName);
  });
}

export function toFixed(value, decimals = 2) {
  const fixedNumber = Number.parseFloat(value).toFixed(decimals).replace(/(\..*[1-9])0+$/, '$1').replace(/\.0*$/, '');
  return Number(fixedNumber);
}

function getPageRecursively(navigations, pageName) {
  // Search in first level first
  for (const index in navigations) {
    const navigation = navigations[index];
    if (navigation.name === pageName) {
      return navigation;
    }
  }
  // Search in other levels after
  for (const index in navigations) {
    const navigation = navigations[index];
    if (navigation.children && navigation.children.length) {
      const page = getPageRecursively(navigation.children, pageName);
      if (page) {
        return page;
      }
    }
  }
}