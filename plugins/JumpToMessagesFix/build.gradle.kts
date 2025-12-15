version = "0.0.5"
description = "Fixes message links not jumping to correct message."

aliucord {
    changelog.set(
        """
        # 0.0.5
        * Fixed observable crashes caused by bad asynchronous execution
            
        # 0.0.4
        * Removed leftover code that was causing bugs
        
        # 0.0.3
        * Attempts to fix inconsistencies yet again.

        # 0.0.2
        * Fixed exception caused by unnecessary onTouch hook trigger
        
        # 0.0.1
        * Initial plugin release.
        """.trimIndent()
    )
    deploy.set(true)
    deployHidden.set(true)
}
