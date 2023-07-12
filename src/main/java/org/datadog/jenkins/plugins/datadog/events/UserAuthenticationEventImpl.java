/*
The MIT License

Copyright (c) 2015-Present Datadog, Inc <opensource@datadoghq.com>
All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package org.datadog.jenkins.plugins.datadog.events;

import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

import java.util.Map;
import java.util.Set;

public class UserAuthenticationEventImpl extends AbstractDatadogSimpleEvent {

    public final static String LOGIN = "authenticated";
    public final static String ACCESS_DENIED = "failed to authenticate";
    public final static String LOGOUT = "logout";

    private String action;

    public UserAuthenticationEventImpl(String username, String action, Map<String, Set<String>> tags) {
        super(tags);
        // Overriding tags set in parent class
        setTags(TagsUtil.merge(TagsUtil.addTagToTags(null, "event_type", SECURITY_EVENT_TYPE), tags));

        if(action == null){
            action = "did something";
        }
        if(username == null){
            username = "anonymous";
        }
        setAggregationKey(username);
        String title = "User " + username + " " + action.toLowerCase();
        setTitle(title);

        String text = "%%% \nUser " + username + " " + action.toLowerCase() +
                "\n" + super.getLocationDetails() + " \n%%%";
        setText(text);

        if (LOGIN.equals(action) || LOGOUT.equals(action)){
            setPriority(Priority.LOW);
            setAlertType(AlertType.SUCCESS);
        } else {
            setPriority(Priority.NORMAL);
            setAlertType(AlertType.ERROR);
        }

        this.action = action;
        this.eventName = "User ";
        if (action.equals(LOGIN)) {
            this.eventName += "Authenticated";
        } else if (action.equals(ACCESS_DENIED)) {
            this.eventName += "failed To Authenticate";
        } else if (action.equals(LOGOUT)) {
            this.eventName += "loggedOut";
        }
    }

    public String getAction() {
        return this.action;
    }
}
