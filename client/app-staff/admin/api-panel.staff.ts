/*
 * Copyright (c) 2018 Kaj Magnus Lindberg
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

/// <reference path="../staff-prelude.staff.ts" />
/// <reference path="oop-method.staff.ts" />


//------------------------------------------------------------------------------
   namespace debiki2.admin {
//------------------------------------------------------------------------------

const r = ReactDOMFactories;



export const ApiPanel = createFactory({
  displayName: 'ApiPanel',

  getInitialState: function() {
    return {};
  },

  componentDidMount: function() {
    Server.listApiSecrets(secrets => {
      if (this.isGone) return;
      this.setState({ secrets });
    });
  },

  componentWillUnmount: function() {
    this.isGone = true;
  },

  createSecret: function() {
    Server.createApiSecret(secret => {
      if (this.isGone) return;
      const secrets = [secret, ...this.state.secrets];
      this.setState({ secrets });
    });
  },

  deleteSecret: function(secretNr: ApiSecretNr) {
    Server.deleteApiSecrets([secretNr], (secrets) => {
      if (this.isGone) return;
      this.setState({ secrets });
    });
  },

  render: function() {
    if (!this.state.secrets)
      return r.p({}, "Loading...");

    const store: Store = this.props.store;

    let elems = this.state.secrets.map((apiSecret: ApiSecret, index: Nr) => {
      return ApiSecretItem({ key: index, apiSecret, deleteSecret: this.deleteSecret });
    });

    const elemsList = elems.length
        ? r.table({ className: 's_A_Api_SecrT' },
            r.thead({}, r.tr({},
              r.th({}, "Nr"),
              r.th({}, "For user"),
              r.th({}, "Created"),
              r.th({}, "Deleted"),
              r.th({}, "Capabilities"),
              r.th({}, "Value and actions"))),
            r.tbody({},
              elems))
        : r.p({ className: 'e_NoApiSecrets' }, "No API secrets.");

    const createSecretButton =
        Button({ onClick: this.createSecret, className: 'e_GenSecrB' },
          "Generate new secret");

    return (
      r.div({ className: 's_A_Api' },
        WebhooksApiPanel(),
        r.h3({}, "API Secrets"),
        r.p({}, "Recent first."),
        elemsList,
        createSecretButton));
  }
});



const ApiSecretItem = createComponent({
  displayName: 'ApiSecretItem',

  showSecret: function(secret: ApiSecret) {
    const userIdAndSecret = `tyid=2:${secret.secretKey}`;
    util.openDefaultStupidDialog({
      body: rFragment({},
        r.p({},
          "Secret value: ", r.code({ className: 'e_SecrVal' }, secret.secretKey)),
        r.p({},
          "cURL example: (note: includes the secret; don't send to anyone)"),
        r.pre({ style: { whiteSpace: 'pre-line' }},
          `curl --user ${userIdAndSecret} ${location.origin}/-/v0/ping`),
        r.p({},
          "This: ", r.code({}, 'tyid=2:..'), " means user 2, which is Sysbot, " +
          "a built-in user that can be used for API requests."),
        r.p({},
          "Here's Sysbot and the secret value, in a Basic Auth HTTP header, " +
          "base 64 encoded: (don't send to anyone!)"),
        r.pre({},
          `Authorization: ${btoa(userIdAndSecret)}`)),
      closeButtonTitle: "Close",
    });
  },

  render: function() {
    const secret: ApiSecret = this.props.apiSecret;
    // Don't show secrets that still works, unless one clicks Show — so less risk that they get exposed.
    const secretKeyOrShowButton = secret.isDeleted
        ? r.s({}, secret.secretKey)
        : Button({ onClick: () => this.showSecret(secret), className: 'e_ShowSecrB' },
            "Show");

    const deleteButton = secret.isDeleted ? null :
      Button({ onClick: () => this.props.deleteSecret(secret.nr) }, "Delete");

    const clazz = 's_ApiSecr';
    const clazzActiveOrDeleted = clazz + (secret.isDeleted ? '-Dd' : '-Active');

    return r.tr({ className: clazz + ' ' + clazzActiveOrDeleted },
      r.td({}, secret.nr),
      r.td({}, "Any"),  // currently may call API using any user id
      r.td({}, timeExact(secret.createdAt)),
      r.td({}, secret.isDeleted ? rFragment("Yes, ", timeExact(secret.deletedAt)) : "No"),
      r.td({}, "Do anything"), // secret.secretType is always for any user
      r.td({}, deleteButton, secretKeyOrShowButton));
  }
});



interface WebhooksApiPanelProps {
}

interface Webhook {
  sendToUrl?: St;
  enabled?: Bo;
}

const WebhooksApiPanel = React.createFactory<WebhooksApiPanelProps>(function(props) {
  const [webhooksBef, setWebhooksBef] = React.useState<Webhook[] | N>(null);
  const [webhooksCur, setWebhooksCur] = React.useState<Webhook[] | N>(null);

  React.useEffect(() => {
    Server.listWebhooks(whks => {
      // If there's not yet any webhook, let's make a new one: {}, which the admin
      // can edit and save.
      const whks2 = whks.length ? whks : [{ id: 1 }];
      setWebhooksBef(whks2);
      setWebhooksCur(whks2);
    });
  }, []);

  /*
  const [url, setUrl] = React.useState({}); // <ValueOk<St>>('');
  const [enabled, setEnabled] = React.useState<Bo>(false);
  */

  if (webhooksCur === null)
    return r.p({}, "Loading webhooks ...");

  const theCurHook = webhooksCur[0];

  function updWebhook(changes: Partial<Webhook>) {
    const curHook = webhooksCur[0];
    const updatedHook = {...curHook, ...changes };
    setWebhooksCur([updatedHook]);   // currently there can be just one webhook
  }

  const urlElm =
      Input({ label: "URL",
          wrapperClassName: 'col-xs-offset-2 col-xs-10',
          className: 'e_WbhkUrl',
          onChange: (event) => {
              updWebhook({ sendToUrl: event.target.value });
            }
          });

  const enabledElm =
      Input({ type: 'checkbox', label: "Enabled",
          wrapperClassName: 'col-xs-offset-2 col-xs-10',
          className: 'e_SearchFld',
          checked: theCurHook.enabled,
          onChange: () => {
            updWebhook({ enabled: !theCurHook.enabled });
          },
          });

  const saveBtn =
      Button({
          //disabled = _.deep
          onClick: () => {
            Server.upsertWebhook(webhooksCur, () => {});
          }, }, "Save");

  return rFr({},
      r.h3({}, "Webhooks"),
      r.p({}, "Currently you can configure one webhook endpoint."),
      urlElm,
      enabledElm,
      saveBtn,
      );
});


//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 fo=r list
