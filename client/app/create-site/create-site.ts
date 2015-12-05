/*
 * Copyright (C) 2015 Kaj Magnus Lindberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/// <reference path="../../typedefs/react/react.d.ts" />
/// <reference path="../plain-old-javascript.d.ts" />
/// <reference path="../ReactStore.ts" />
/// <reference path="../Server.ts" />

//------------------------------------------------------------------------------
   module debiki2.createsite {
//------------------------------------------------------------------------------

var d = { i: debiki.internal, u: debiki.v0.util };
var r = React.DOM;
var reactCreateFactory = React['createFactory'];

var ReactBootstrap: any = window['ReactBootstrap'];
var Button = reactCreateFactory(ReactBootstrap.Button);
var ButtonGroup = reactCreateFactory(ReactBootstrap.ButtonGroup);
var Panel = reactCreateFactory(ReactBootstrap.Panel);
var Input = reactCreateFactory(ReactBootstrap.Input);
var ButtonInput = reactCreateFactory(ReactBootstrap.ButtonInput);

var ReactRouter = window['ReactRouter'];
var Route = reactCreateFactory(ReactRouter.Route);
var Redirect = reactCreateFactory(ReactRouter.Redirect);
var DefaultRoute = reactCreateFactory(ReactRouter.DefaultRoute);
var NotFoundRoute = reactCreateFactory(ReactRouter.NotFoundRoute);
var RouteHandler = reactCreateFactory(ReactRouter.RouteHandler);
var RouterNavigationMixin = ReactRouter.Navigation;
var RouterStateMixin = ReactRouter.State;



export function routes() {
  return Route({ path: '/', handler: CreateSiteComponent },
    /* Later, if one should choose between a site and embedded comments:
    DefaultRoute({ handler: ChooseSiteTypeComponent }),
    Route({ name: 'create-simple-site', path: 'create-simple-site', handler: CreateSimpleSiteComponent }),
    */
    DefaultRoute({ handler: CreateSimpleSiteComponent }),
    Route({ name: 'create-embedded-comments', path: 'create-embedded-comments',
      handler: CreateEmbeddedSiteComponent }));
}



var CreateSiteComponent = React.createClass({
  mixins: [RouterStateMixin],

  getInitialState: function() {
    return {
      pricePlan: this.getQuery().pricePlan
    };
  },

  render: function() {
    return RouteHandler({ pricePlan: this.state.pricePlan });
  }
});


/* Later, if I make embedded comments work again:
var ChooseSiteTypeComponent = React.createClass({
  mixins: [RouterNavigationMixin],

  goToCreateSimpleSite: function() {
    this.transitionTo('create-simple-site');
  },

  goToCreateEmbeddedSite: function() {
    this.transitionTo('create-embedded-comments');
  },

  render: function() {
    return r.div({},
      r.h1({}, 'Choose Site Type'),
      r.p({}, 'What do you want to create? (You can change your mind later.)'),
      r.div({},
        Button({ onClick: this.goToCreateSimpleSite }, 'A forum, bog or simple website')));
        // Button({ onClick: this.goToCreateEmbeddedSite }, 'Embedded comments')));
  }
}); */



var CreateSimpleSiteComponent = React.createClass({
  getInitialState: function() {
    return { showErrors: false };
  },

  handleSubmit: function(event) {
    event.preventDefault();
    var anyError =
        !this.refs.terms.areTermsAccepted() ||
        this.refs.localHostname.findAnyError();
        // COULD test email addr too
    if (anyError) {
      this.setState({ showErrors: true });
      return;
    }
    Server.createSite(
        this.refs.emailAddress.getValue(),
        this.refs.localHostname.getValue(),
        null,
        this.props.pricePlan,
        (newSiteOrigin) => {
          window.location.assign(newSiteOrigin);
        });
  },

  render: function() {
    return (
      r.div({},
        r.h1({}, 'Create Site'),
        r.form({ className: 'esCreateSite', onSubmit: this.handleSubmit },
          Input({ type: 'text', label: 'Email', id: 'e2eEmail', className: 'esCreateSite_email',
              placeholder: 'your-email@example.com',
              help: 'Your email address, which you will use to login and ' +
                'administrate the site.', ref: 'emailAddress' }),

          LocalHostnameInput({ label: 'Site Address', placeholder: 'your-site-name',
              help: 'The address of your new site. (You can map a custom domain name ' +
                'to your site later, on the site settings pages, so your site would be ' +
                'reachable via e.g. www.your-domain.com. I have not yet implemented ' +
                'this though.)',
              ref: 'localHostname', showErrors: this.state.showErrors }),

          AcceptTerms({ ref: 'terms' }),

          ButtonInput({ type: 'submit', value: 'Create Site', bsStyle: 'primary' }))));
  }
});



// Currently not in use; embedded comments disabled & broken right now.
var CreateEmbeddedSiteComponent = React.createClass({
  getInitialState: function() {
    return { showErrors: false };
  },

  handleSubmit: function(event) {
    event.preventDefault();
    var anyError =
        !this.refs.terms.areTermsAccepted() ||
        this.refs.localHostname.findAnyError() ||
        !this.refs.embeddingSiteAddress.isValid();
        // COULD test email too
    if (anyError) {
      this.setState({ showErrors: true });
      return;
    }
    Server.createSite(
        this.refs.emailAddress.getValue(),
        this.refs.localHostname.getValue(),
        this.refs.embeddingSiteAddress.getValue(),
        this.props.pricePlan,
        (newSiteOrigin) => {
          window.location.assign(newSiteOrigin);
        });
  },

  updateDebikiAddress: function() {
    var newEmbeddingSiteAddress = this.refs.embeddingSiteAddress.getValue();
    var newLocalHostname = deriveLocalHostname(newEmbeddingSiteAddress);
    this.refs.localHostname.setValue(newLocalHostname);
  },

  render: function() {
    return (
      r.div({},
        r.h1({}, 'Create Embedded Site'),
        r.form({ onSubmit: this.handleSubmit },
          Input({ type: 'text', label: 'Email', placeholder: 'your-email@example.com',
              help: 'Your email address, which you will use to login and moderate comments.',
              ref: 'emailAddress' }),

          EmbeddingAddressInput({ ref: 'embeddingSiteAddress',
              onChange: this.updateDebikiAddress }),

          LocalHostnameInput({ label: 'Debiki Address', ref: 'localHostname',
              help: 'This is where you login to moderate embedded comments.',
              showErrors: this.state.showErrors }),

          AcceptTerms({ ref: 'terms' }),

          ButtonInput({ type: 'submit', value: 'Create Site', bsStyle: 'primary' }))));
  }
});



// Currently not in use; embedded comments disabled & broken right now.
export var EmbeddingAddressInput = createClassAndFactory({
  getInitialState: function() {
    return { bsStyle: 'error' };
  },
  isValid: function() {
    var value = this.refs.input.getValue();
    var valid = /https?:\/\/.+/.test(value);
    return valid;
  },
  getValue: function() {
    return this.refs.input.getValue();
  },
  onChange: function(event) {
    this.setState({ bsStyle: this.isValid() ? 'success' : 'error' });
    if (this.props.onChange) {
      this.props.onChange(event);
    }
  },
  render: function() {
    return (
      Input({ type: 'text', label: this.props.label || 'Embedding Site Address',
        placeholder: 'https://www.example.com', onChange: this.onChange,
        ref: 'input', bsStyle: this.state.bsStyle, help: this.props.help ||
          'The address of the website where the embedded comments should appear.' }));
  }
});



var AcceptTerms = createClassAndFactory({
  areTermsAccepted: function() {
    return this.refs.checkbox.getChecked();
  },

  render: function() {
    var label =
      r.span({},
          'I accept the ',
          r.a({ href: '/-/terms-of-use', target: '_blank'}, 'Terms of Use'),
          ' and the ',
          r.a({ href: '/-/privacy-policy', target: '_blank' }, 'Privacy Policy'));

    return (
      Input({ type: 'checkbox', label: label, ref: 'checkbox', id: 'e2eAcceptTerms' }));
  }
});



/**
 * Don't make the domain editable at this point, because then some people would edit it,
 * without realizing or remembering that they need to update their domain name server
 * records before this'll work. So instead: 1) let people create a default-domain site,
 * and later 2) let them connect it to their own main domain, from the admin pages.
 * Then they can return to the default-domain address, if they mess up, and fix things.
 */
var LocalHostnameInput = createClassAndFactory({
  getInitialState: function() {
    return { value: '' }
  },

  setValue: function(newValue) {
    this.setState({ value: newValue });
  },

  getValue: function() {
    return this.state.value;
  },

  onChange: function(event) {
    this.setState({ value: event.target.value });
  },

  findAnyError: function() {
    if (this.state.value.length < 6)
      return 'The name should be at least six characters long.';

    if (!/^[a-z][a-z0-9-]*[a-z0-9]$/.test(this.state.value))
      return 'Please use only lowercase letters a-z and 0-9, e.g. "my-new-website"';

    return null;
  },

  render: function() {
    var value = this.state.value;

    var anyError;
    if (this.props.showErrors) {
      var anyError = this.findAnyError();
      if (anyError) {
        anyError = Panel({ header: 'Bad address', bsStyle: 'danger' }, anyError );
      }
    }

    return (
      r.div({ className: 'form-group' + (anyError ? ' has-error' : '') },
        r.label({ htmlFor: 'dwLocalHostname' }, this.props.label),
        r.br(),
        r.kbd({}, location.protocol + '//'),
        r.input({ type: 'text', id: 'dwLocalHostname', className: 'form-control',
            placeholder: this.props.placeholder, ref: 'input', onChange: this.onChange,
            value: value }),
        r.kbd({}, '.' + debiki.baseDomain),
        r.p({ className: 'help-block' }, this.props.help),
        anyError));
  }
});


/**
 * Converts e.g. 'https://www.my-lovely.site.com' to 'my-lovely-site'.
 */
function deriveLocalHostname(embeddingSiteAddress) {
  var debikiAddress = embeddingSiteAddress;
  // Remove protocol.
  debikiAddress = debikiAddress.replace(/^[a-z]+:\/\//, '');
  // Remove port.
  debikiAddress = debikiAddress.replace(/:[0-9]+$/, '');
  // Remove top level domain.
  debikiAddress = debikiAddress.replace(/\.[a-z]*$/, '');
  // Remove any rather uninteresting leading 'www'.
  debikiAddress = debikiAddress.replace(/^www\./, '');
  // Replace '.' and other weird chars with '-'.
  debikiAddress = debikiAddress.replace(/[^a-z0-9]/g, '-');
  return debikiAddress;
}


//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 fo=r list
