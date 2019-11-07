/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const React = require('react');

const CompLibrary = require('../../core/CompLibrary.js');

const MarkdownBlock = CompLibrary.MarkdownBlock; /* Used to read markdown */
const Container = CompLibrary.Container;
const GridBlock = CompLibrary.GridBlock;

class HomeSplash extends React.Component {
  render() {
    const {siteConfig, language = ''} = this.props;
    const {baseUrl, docsUrl} = siteConfig;
    const docsPart = `${docsUrl ? `${docsUrl}/` : ''}`;
    const langPart = `${language ? `${language}/` : ''}`;
    const docUrl = doc => `${baseUrl}${docsPart}${langPart}${doc}`;

    const SplashContainer = props => (
      <div className="homeContainer">
        <div className="homeSplashFade">
          <div className="wrapper homeWrapper">{props.children}</div>
        </div>
      </div>
    );

    const Logo = props => (
      <div className="projectLogo">
        <img src={props.img_src} alt="Project Logo" />
      </div>
    );

    const ProjectTitle = () => (
      <div>
        <h2 className="projectTitle">
          <small>{siteConfig.tagline}</small>
        </h2>
        <h3 className="projectTitleSmall">
          From Static Data to a Dynamic API in Minutes
        </h3>
      </div>
    );

    const PromoSection = props => (
      <div className="section promoSection">
        <div className="promoRow">
          <div className="pluginRowBlock">{props.children}</div>
        </div>
      </div>
    );

    const Button = props => (
      <div className="pluginWrapper buttonWrapper">
        <a className="button" href={props.href} target={props.target}>
          {props.children}
        </a>
      </div>
    );

    return (
      <SplashContainer>
        <div className="inner">
          <img
            src={`${baseUrl}img/franklin-logo-tagline@2x.png`}
            alt="Azavea"
            width="30%"
          />
          <ProjectTitle siteConfig={siteConfig} />
        </div>
      </SplashContainer>
    );
  }
}

class Index extends React.Component {
  render() {
    const {config: siteConfig, language = ''} = this.props;
    const {baseUrl} = siteConfig;

    const Block = props => (
      <Container
        padding={['bottom', 'top']}
        id={props.id}
        background={props.background}>
        <GridBlock
          align="left"
          contents={props.children}
          layout={props.layout}
        />
      </Container>
    );

    const FeatureCallout = () => (
      <div
        className="productShowcaseSection paddingBottom"
        style={{textAlign: 'center'}}>
        <h2></h2>
      </div>
    );

    const TryOut = () => (
      <Block id="try">
        {[
          {
            content:
              'Designed to be simple to use while easy to extend, you can choose how to get started. Use ' +
              'our published `docker` images to get a server running and deployed quickly or hack on the ' +
              '[source](https://github.com/azavea/franklin/) and use it directly in your own application.',
            image: `${baseUrl}img/franklin-startup.png`,
            imageAlign: 'left',
            title: 'Create a Web Service with a Single Command',
          },
        ]}
      </Block>
    );

    const Description = () => (
      <Block background="dark">
        {[
          {
            content:
              'Franklin is built on top of powerful libraries like [**GeoTrellis**]() and **PostGIS**. With these libraries your ' +
              'catalogs and features can be exposed as vector and raster tile services. Combined with the API, Franklin allows you to build ' +
              'quickly so you can spend more time on what\'s really important: the data',
            image: `${baseUrl}img/annotation-tool.png`,
            imageAlign: 'right',
            title: 'Visualize Your Catalog and Features',
          },
        ]}
      </Block>
    );

    const LearnHow = () => (
      <Block background="light">
        {[
          {
            content:
              'Libraries like [**PyStac**](https://pystac.readthedocs.io/en/latest/) allow you to create and manipulate static STAC datasets. **Franklin** ' +
              'complements these libraries by allowing you to create a dynamic STAC/OGC API Features web service from ' +
              'these those static datasets.',
            image: `${baseUrl}img/undraw_server_q2pb.svg`,
            imageAlign: 'right',
            title: 'Import Existing Static STAC Catalogs',
          },
        ]}
      </Block>
    );


    const Features = () => (
      <Block layout="twoColumn">
        {[
          {
            content: 'This is the content of my feature',
            image: `${baseUrl}img/undraw_react.svg`,
            imageAlign: 'top',
            title: 'Feature One',
          },
          {
            content: 'The content of my second feature',
            image: `${baseUrl}img/undraw_operating_system.svg`,
            imageAlign: 'top',
            title: 'Feature Two',
          },
        ]}
      </Block>
    );

    const Showcase = () => {
      if ((siteConfig.users || []).length === 0) {
        return null;
      }

      const showcase = siteConfig.users
        .filter(user => user.pinned)
        .map(user => (
          <a href={user.infoLink} key={user.infoLink}>
            <img src={user.image} alt={user.caption} title={user.caption} />
          </a>
        ));

      const pageUrl = page => baseUrl + (language ? `${language}/` : '') + page;

      return (
        <div className="productShowcaseSection paddingBottom">
          <h2>Who is Using This?</h2>
          <p>This project is used by all these people</p>
          <div className="logos">{showcase}</div>
          <div className="more-users">
            <a className="button" href={pageUrl('users.html')}>
              More {siteConfig.title} Users
            </a>
          </div>
        </div>
      );
    };

    return (
      <div>
        <HomeSplash siteConfig={siteConfig} language={language} />
        <div className="mainContainer">
          <FeatureCallout />
          <LearnHow />
          <TryOut />
          <Description />
          <Showcase />
        </div>
      </div>
    );
  }
}

module.exports = Index;
