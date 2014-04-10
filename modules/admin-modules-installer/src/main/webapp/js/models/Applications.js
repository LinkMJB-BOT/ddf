/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
/*global define*/
define(function (require) {
    "use strict";

    var Backbone = require('backbone'),
        $ = require('jquery'),
        _ = require('underscore');
    var Applications = {};

    Applications.MvnUrlColl = Backbone.Collection.extend({
        configUrl: "/jolokia/exec/org.codice.ddf.admin.application.service.ApplicationService:service=application-service",
        collectedData: function () {
            var data = {
                type: 'EXEC',
                mbean: 'org.codice.ddf.admin.application.service.ApplicationService:service=application-service',
                operation: 'addApplications'
            };
            data.arguments = [];
            data.arguments.push(this.toJSON());
            return data;
        },
        save: function () {
            var addUrl = [this.configUrl, "addApplications"].join("/");
            var collect = this.collectedData();
            var jData = JSON.stringify(collect);

            return $.ajax({
                    type: 'POST',
                    contentType: 'application/json',
                    data: jData,
                    url: addUrl
                });
        }
    });
    var startUrl = '/jolokia/exec/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/startApplication/';
    var stopUrl = '/jolokia/exec/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/stopApplication/';

    var versionRegex = /([^0-9]*)([0-9]+.*$)/;
    Applications.TreeNode = Backbone.Model.extend({
       defaults: function() {
            return {
                selected: false
            };
       },

       initialize: function(){
           var children = this.get("children");
           var that = this;
           this.massageVersionNumbers();
           this.cleanupDisplayName(); //set({displayName: this.createDisplayName()});
           this.updateName();
           this.set({currentState: this.get("state") === "ACTIVE"});
           this.set({selected: this.get("currentState")});
           if (children){
               this.set({children: new Applications.TreeNodeCollection(children)});
               this.get("children").forEach(function (child) {
                   child.set({parent: that});
               });
           }
           this.listenTo(this, "change", this.updateModel);
       },
       updateModel: function(){
         if (this.get("selected")) {
             if (this.get("parent")) {
             this.get("parent").set({selected: true});
             }
         } else if (this.get("children").length){
             this.get("children").forEach(function(child) {
                 child.set({selected: false});
             });
         }
       },

        updateName: function() {
            //this.set({name: this.get("name").replace(/\./g,'')});
            this.set({appId: this.get("name").replace(/\./g,'')});
        },

        // Some apps come in having the version number included
        // as part of the app name - e.g. search-app-2.3.1.ALPHA3-SNAPSHOT.
        // This function strips the version from the display name and
        // places it in the version variable so the details show correctly.
        massageVersionNumbers: function() {
            this.set({displayName: this.get("name")});
            if (this.get("version") === "0.0.0") {
                var matches = this.get("name").match(versionRegex);
                if (matches.length === 3) {
                    this.set({displayName: matches[1]});
                    this.set({version: matches[2]});
                }
            }
        },

        cleanupDisplayName: function(){
            var tempName = this.get("displayName"); //.replace(/\./g,'');
            var names = tempName.split('-');
            var dispName = "";
            var that = this;
            _.each(names, function(name) {
                if (dispName.length > 0) {
                    dispName = dispName + " ";
                }
                dispName = dispName + that.capitalizeFirstLetter(name);
            });
            this.set({displayName: dispName});
        },

       capitalizeFirstLetter: function(string){
           if (string && string !== ""){
               return string.charAt(0).toUpperCase() + string.slice(1);
           }
            return string;
       },

        isDirty: function() {
            return (this.get("selected") !== this.get("currentState"));
        },

        countDirty: function() {
            var count = 0;
            if (this.isDirty()) {
                count = 1;
            }
            if (this.get("children").length){
                this.get("children").forEach(function(child) {
                    count += child.countDirty();
                });
            }
            return count;
        },

        // bottom-up uninstall
        uninstall: function(statusUpdate) {
            if (this.countDirty() > 0){
                // uninstall all needed children
                if (this.get("children").length){
                    this.get("children").forEach(function(child) {
                        child.uninstall(statusUpdate);
                    });
                }
                // uninstall myself
                if (!this.get("selected") && this.isDirty()) {
                    this.save(statusUpdate);
                }
            }
        },

        // top-down install
        install: function(statusUpdate) {
            if (this.countDirty() > 0){
                // install myself
                if (this.get("selected") && this.isDirty()) {
                    this.save(statusUpdate);
                }

                // install my needed children
                if (this.get("children").length){
                    this.get("children").forEach(function(child) {
                        child.install(statusUpdate);
                    });
                }
            }
        },

        // override save to actual invoke the install of this app
        save: function(statusUpdate){
            if (this.isDirty()) {
                if (this.get("selected")) {
                    statusUpdate("Installing " + this.get("name"));
                    console.log("Installing " + this.get("name"));
                    $.ajax({
                        type: "GET",
                        url: startUrl + this.get("name") + '/',
                        dataType: "JSON",
                        async: false,
                        success: function(response, statusTxt) {
                            console.log("Returned from install: " + response.value + " status: "+ statusTxt);
                        },
                        error: function(response, statusTxt) {
                            console.log("Returned from install: " + response + " status: " + statusTxt);
                        }
                    });

                } else {
                    statusUpdate("Uninstalling " + this.get("name"));
                    console.log("Uninstalling " + this.get("name"));
                    $.ajax({
                        type: "GET",
                        url: stopUrl + this.get("name") + '/',
                        dataType: "JSON",
                        async: false,
                        success: function(response, statusTxt) {
                            console.log("Returned from uninstall: " + response.value + " status: "+ statusTxt);
                        },
                        error: function(response, statusTxt) {
                            console.log("Returned from uninstall: " + response + " status: " + statusTxt);
                        }
                    });
                }
            }
        }



    });

    Applications.TreeNodeCollection = Backbone.Collection.extend({
        model: Applications.TreeNode,
        url: '/jolokia/read/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/ApplicationTree/',

        // instead of a single post of the collection, we are making individual
        // calls to install/uninstall specific items of the collection (apps)
        // corresponding to their selection status and current state
        sync: function(method, model, options){
            var statusUpdate = options.statusUpdate;
            var thisModel = model;
            if (method === 'read'){
                var appResponse = new Applications.Response();
                appResponse.fetch({
                    success: function(model){
                        thisModel.reset(model.get("value"));
                        console.log("Reloaded application list");
                    }
                });
            } else { // this is a save of the model (CUD)
                this.save(statusUpdate);
            }
        },

        save: function(statusUpdate) {
            // get the total number of apps to be installed/uninstalled
            var count = 0;
            var totalCount = 0;
            this.each(function(child) {
               totalCount += child.countDirty();
            });

            // uninstall apps first
            this.each(function(child) {
                child.uninstall(function(message) {
                    if (typeof statusUpdate !== 'undefined') {
                        statusUpdate(message, count/totalCount*100);
                    }
                    count++;
                });
            });

            // then install necessary apps
            this.each(function(child) {
                child.install(function(message) {
                    if (typeof statusUpdate !== 'undefined') {
                        statusUpdate(message, count/totalCount*100);
                    }
                    count++;
                });
            });

            if (typeof statusUpdate !== 'undefined') {
                statusUpdate("Total of " + totalCount + " applications installed/uninstalled.", 100);
            }
        }

    });

    Applications.Response = Backbone.Model.extend({
        url: '/jolokia/read/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/ApplicationTree/'
    });

    return Applications;

});