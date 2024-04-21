<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>
<script type="text/javascript" src="<c:url value='/script/stomp-7.0.umd.min.js'/>"></script>
<script type="text/javascript">
    var csrfheaderName = "${_csrf.headerName}";
    var csrftoken = "${_csrf.token}";
    var StompClient = {
        stompClient: null,
        onConnect: [],
        onConnecting: [],
        onDisconnect: [],
        onError: [],
        send: function(dest, msg) {
            this.connect(function(stompclient) {
                stompclient.stompClient.publish({ destination: dest, body: msg });
            });
        },
        state: 'dc',
        outstandingConnectionCallbacks: [],
        resubscriptionCallback: null,
        __doConnectionCallbacks: function(stompclient) {
            // resubscribe to everything first if needed
            if (stompclient.resubscriptionCallback != null) {
                stompclient.resubscriptionCallback();
                stompclient.resubscriptionCallback = null;
            }
            while(stompclient.outstandingConnectionCallbacks.length > 0) {
                var callback = stompclient.outstandingConnectionCallbacks.pop();
                try {
                    callback(stompclient);
                } catch(e) {
                    console.log("Could not execute a postConnect callback", e);
                }
            }
        },
        connect: function(postConnectCallback) {
            var stompclient = this;
            if (postConnectCallback) {
                stompclient.outstandingConnectionCallbacks.push(postConnectCallback);
            }
            if (stompclient.stompClient == null) {
                var headers = {};
                if (csrfheaderName != "" && csrftoken != "") {
                    headers[csrfheaderName] = csrftoken;
                }
                stompclient.stompClient = new StompJs.Client({
                    brokerURL: "${wsUri}",
                    heartbeatIncoming: 20000,
                    heartbeatOutgoing: 20000,
                    connectHeaders: headers
                });
                stompclient.stompClient.onConnect = (frame) => {
                    console.log('Connected', frame);
                    stompclient.state='connected';
                    for (var i = 0; i < stompclient.onConnect.length; i++) {
                        try {
                            stompclient.onConnect[i](frame, stompclient);
                        } catch (e) {
                            console.log("Could not execute an onConnect callback", e);
                        }
                    }
                    stompclient.__doConnectionCallbacks(stompclient);
                };

                stompclient.stompClient.onWebSocketError = (error) => {
                    console.error('Websocket Error', error);
                    for (var i = 0; i < stompclient.onError.length; i++) {
                        try {
                            stompclient.onError[i](frame, stompclient);
                        } catch (e) {
                            console.log("Could not execute an onError callback", e);
                        }
                    }
                };

                stompclient.stompClient.onStompError = (frame) => {
                    console.error('Broker reported Stomp error: ' + frame.headers['message']);
                    console.error('Additional details: ' + frame.body);
                };

                stompclient.stompClient.onDisconnect = (frame) => {
                    console.log('Disconnected', frame);
                    stompclient.stompClient = null;
                    stompclient.state='dc';
                    for (var i = 0; i < stompclient.onDisconnect.length; i++) {
                        try {
                            stompclient.onDisconnect[i](frame, stompclient);
                        } catch (e) {
                            console.log("Could not execute an onDisconnect callback", e);
                        }
                    }
                };

                // resubscribe to everything again
                stompclient.resubscriptionCallback = function() {
                    for (var topic in stompclient.subscriptions) {
                        stompclient.__doSubscription(stompclient, topic, stompclient.subscriptions[topic]);
                    }
                };
            }

            if (stompclient.state == 'connecting') {
                // whenever the connections is made, the callbacks will happen
                return;
            } else if (stompclient.state == 'connected') {
                stompclient.__doConnectionCallbacks(stompclient);
            } else {
                stompclient.state = 'connecting';
                for (var i = 0; i < stompclient.onConnecting.length; i++) {
                    try {
                        stompclient.onConnecting[i](stompclient);
                    } catch (e) {
                        console.log("Could not execute an onConnecting callback", e);
                    }
                }

                stompclient.stompClient.activate();
            }
        },
        // object containing:
        // - 'subscription-topic': {'owner1': callback1(msg), 'owner2': callback2(msg)}
        subscriptions: {},
        unsubscriptions: {},
        subscribe: function(owner, subscriptions, postSubCallback) {
            this.connect(function(stompclient) {
                //add subscriptions to the callback obj
                for (var topic in subscriptions) {
                    var subCallbacks = stompclient.subscriptions[topic];
                    if (!subCallbacks || stompclient.resubscribeEveryTime(topic)) {
                        // subscribe to topic on server
                        var subCallbacks = {};
                        stompclient.__doSubscription(stompclient, topic, subCallbacks);
                    }

                    // add the actual callback
                    subCallbacks[owner] = subscriptions[topic];
                }

                if (postSubCallback) {
                    postSubCallback();
                }
            });
        },
        __doSubscription: function(stompclient, topic, subCallbacks) {
            stompclient.subscriptions[topic] = subCallbacks;
            stompclient.unsubscriptions[topic] = stompclient.stompClient.subscribe(topic, function(msg) {
                for (var owner in subCallbacks) {
                    try {
                        subCallbacks[owner](msg);
                    } catch(e) {
                        console.log("Could not execute an incoming message callback", topic, owner, e);
                    }
                }
            });
        },
        resubscribeEveryTime: function(topic) {
            return topic.startsWith("/app/");
        },
        unsubscribe: function(topic, owner) {
            if (topic && owner && this.subscriptions[topic] && this.subscriptions[topic][owner]) {
                delete this.subscriptions[topic][owner];
            }
            // can also delete the subscription to the topic itself (if that is the last owner), but not necessary
        },
        unsubscribeOwner: function(owner) {
            for (var topic in this.subscriptions) {
                this.unsubscribe(topic, owner);
            }
        },
        disconnect: function() {
            var stompclient = this;
            if (stompclient.stompClient != null) {
                stompclient.stompClient.deactivate();
                console.log('Disconnected from Airsonic websocket');
            }
        }
    }
</script>
