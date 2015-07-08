[#-- a banner to be displayed when the space taken by artifacts exceeds the hard limit --]
[#if ctx.darkFeatureService.artifactStorageSpaceLimited]
    [#assign userLoggedIn = ctx.getUser(req)?? /]
    [#if userLoggedIn && ctx.storageCappingService.hardLimitExceeded]
        [@ui.messageBox type='error' cssClass='storage-hard-limit-warning-banner']
        <strong>
            [#if ctx.hasAdminPermission()]
                [@s.text name="storage.capping.warning.banner.admin"]
                    [@s.param][@s.url namespace='/admin' action='configureArtifactStorage' /][/@s.param]
                [/@s.text]
            [#else]
                [@s.text name="storage.capping.warning.banner.nonAdmin" /]
            [/#if]
        </strong>
        [/@ui.messageBox]
    [/#if]
[/#if]