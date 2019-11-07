/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// See https://docusaurus.io/docs/site-config for all the possible
// site configuration options.

// List of projects/orgs using your project for the users page.
const users = [
  /* someday...
  {
    caption: 'User1',
    // You will need to prepend the image path with your baseUrl
    // if it is not '/', like: '/test-site/img/image.jpg'.
    image: '/img/undraw_open_source.svg',
    infoLink: 'https://www.facebook.com',
    pinned: true,
  },
  */
];

const siteConfig = {
  title: "Franklin", // Title for your website.
  tagline: "A STAC and OGC API Features compliant Server",
  url: "https://azavea.github.io", // Your website URL
  baseUrl: "/franklin/", // Base URL for your project */
  // For github.io type URLs, you would set the url and baseUrl like:
  //   url: 'https://facebook.github.io',
  //   baseUrl: '/test-site/',


  disableHeaderTitle: true,
  // Used for publishing and more
  projectName: "franklin",
  organizationName: "azavea",
  // For top-level user or org sites, the organization is still the same.
  // e.g., for the https://JoelMarcey.github.io site, it would be set like...
  //   organizationName: 'JoelMarcey'

  // For no header links in the top nav bar -> headerLinks: [],
  headerLinks: [
    { doc: "introduction", label: "Getting Started" },
  ],

  // If you have users set above, you add it here:
  users,

  /* path to images for header/footer */
  headerIcon: "img/header.png",
  footerIcon: "img/footer.png",
  favicon: "img/favicon.ico",

  /* Colors for website */
  colors: {
    primaryColor: "#092c39",
    secondaryColor: "#5c5543"
  },

  stylesheets: ["https://fonts.googleapis.com/css?family=Roboto|Roboto+Condensed:400,700&display=swap"],

  /* Custom fonts for website */
//   font-family: 'Roboto Condensed', sans-serif;
// font-family: 'Roboto', sans-serif;
//   fonts: {
//     myFont: [
//       "Roboto Condensed",
//       "Sans-Serif"
//     ],
//     myOtherFont: [
//       "Roboto",
//       "system-ui"
//     ]
//   },

  // This copyright info is used in /core/Footer.js and blog RSS/Atom feeds.
  copyright: `Copyright © ${new Date().getFullYear()} Azavea`,

  highlight: {
    // Highlight.js theme to use for syntax highlighting in code blocks.
    theme: "default"
  },

  // Add custom scripts here that would be placed in <script> tags.
  scripts: ["https://buttons.github.io/buttons.js"],

  // On page navigation for the current documentation page.
  onPageNav: "separate",
  // No .html extensions for paths.
  cleanUrl: true,

  // Open Graph and Twitter card images.
  ogImage: "img/franklin-logo-tagline.svg",
  twitterImage: "img/franklin-logo-tagline.svg",

  // For sites with a sizable amount of content, set collapsible to true.
  // Expand/collapse the links and subcategories under categories.
  // docsSideNavCollapsible: true,

  // Show documentation's last contributor's name.
  // enableUpdateBy: true,

  // Show documentation's last update time.
  // enableUpdateTime: true,

  // You may provide arbitrary config keys to be used as needed by your
  // template. For example, if you need your repo's URL...
  repoUrl: "https://github.com/azavea/franklin",
  customDocsPath: "api-docs/target/mdoc"
};

module.exports = siteConfig;
