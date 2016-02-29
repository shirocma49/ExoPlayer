package jp.co.pixela.exoplayerwrapper;

/**
 * Created by kumam on 2016/02/26.
 */
public enum PlayerState
{
    Initialized("STATE_INITIALIZED")
    ,Playing("STATE_PLAYING")
    ,Pause("STATE_PAUSE")
    ,None("STATE_NONE")
    ;

    private final String text;

    private PlayerState(final String text) {
        this.text = text;
    }
}
