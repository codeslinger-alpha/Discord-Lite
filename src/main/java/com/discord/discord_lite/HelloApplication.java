package com.discord.discord_lite;

import com.discord.discord_lite.client.ClientEvent;
import com.discord.discord_lite.client.ClientEventType;
import com.discord.discord_lite.client.LanClientService;
import com.discord.discord_lite.model.Channel;
import com.discord.discord_lite.model.ChannelGroup;
import com.discord.discord_lite.model.ChatMessage;
import com.discord.discord_lite.model.DirectMessage;
import com.discord.discord_lite.model.MessageAttachment;
import com.discord.discord_lite.model.MessageReply;
import com.discord.discord_lite.model.MessageReaction;
import com.discord.discord_lite.model.Role;
import com.discord.discord_lite.model.UserProfileDetails;
import com.discord.discord_lite.model.UserStatus;
import com.discord.discord_lite.model.UserSummary;
import com.discord.discord_lite.model.WorkspaceServer;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.geometry.Side;
import org.kordamp.ikonli.javafx.FontIcon;

public class HelloApplication extends Application {
    private static final String MESSAGE_ACTIONS_PINNED_KEY = "messageActionsPinned";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d.MM.yyyy");
    private static final DateTimeFormatter PROFILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy")
        .withZone(ZoneId.systemDefault());
    private static final List<EmojiOption> EMOJI_CATALOG = loadEmojiCatalog();
    private static final Map<String, String> EMOJI_CODEPOINTS = buildEmojiCodepointMap(EMOJI_CATALOG);
    private static final Path EMOJI_CACHE_DIRECTORY = Path.of("data", "cache", "emoji");
    private static final List<String> EMOJI_IMAGE_BASE_URLS = List.of(
        "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/",
        "https://raw.githubusercontent.com/twitter/twemoji/v14.0.2/assets/72x72/"
    );
    private static final int PROFILE_IMAGE_MAX_BYTES = 1_500_000;
    private static final int SERVER_ICON_MAX_BYTES = 1_500_000;
    private static final int SERVER_COVER_MAX_BYTES = 3_500_000;
    private static final int MESSAGE_ATTACHMENT_MAX_COUNT = 10;
    private static final int MESSAGE_ATTACHMENT_MAX_BYTES = 8_000_000;
    private static final int MESSAGE_ATTACHMENT_TOTAL_MAX_BYTES = 24_000_000;

    private final LanClientService service = new LanClientService();
    private final ListView<WorkspaceServer> serverListView = new ListView<>();
    private final ListView<Object> channelListView = new ListView<>();
    private final ListView<UserSummary> dmListView = new ListView<>();
    private final ListView<Object> memberListView = new ListView<>();
    private final ListView<Object> messageListView = new ListView<>();
    private final TextField messageInput = new TextField();
    private final TextFlow messageInputPreview = new TextFlow();
    private final TextField memberSearchField = new TextField();
    private final List<MessageAttachment> pendingComposerAttachments = new ArrayList<>();
    private String composerAttachmentConversationKey;
    private final List<UserSummary> allChannelMembers = new ArrayList<>();
    private final List<ChannelGroup> loadedChannelGroups = new ArrayList<>();
    private final Set<String> collapsedChannelGroupIds = new HashSet<>();
    private final Map<String, ChannelGroup> channelGroupById = new HashMap<>();
    private final ContextMenu mentionSuggestionPopup = new ContextMenu();
    private final List<MentionSuggestion> activeMentionSuggestions = new ArrayList<>();
    private final Label sessionLabel = new Label("Disconnected");
    private final Label channelLabel = new Label("No conversation selected");
    private final Label typingLabel = new Label("");
    private final Label unreadLabel = new Label("Unread: 0");

    private final PauseTransition typingPause = new PauseTransition(Duration.seconds(1.2));
    private ConversationMode activeMode = ConversationMode.NONE;
    private AppPage currentPage = AppPage.LOGIN;
    private String themeStylesheet;
    private StackPane pageContainer;
    private BorderPane chatPageRoot;
    private Node welcomePageRoot;
    private Node loginPageRoot;
    private Node registerPageRoot;
    private TextField loginHostField;
    private TextField loginPortField;
    private TextField loginUsernameField;
    private PasswordField loginPasswordField;
    private Label loginErrorLabel;
    private TextField registerHostField;
    private TextField registerPortField;
    private TextField registerNameField;
    private TextField registerUsernameField;
    private PasswordField registerPasswordField;
    private PasswordField registerConfirmPasswordField;
    private Label registerErrorLabel;
    private Label registerUsernameHintLabel;
    private StackPane registerAvatarPreview;
    private String registerSelectedAvatarBase64;
    private Button dmHomeButton;
    private Button contextSettingsButton;
    private Label contextHeaderLabel;
    private Button contextActionButton;
    private Button contextGroupActionButton;
    private Button contextInviteButton;
    private VBox channelSectionPane;
    private VBox dmSectionPane;
    private StackPane serverBannerPane;
    private VBox memberPane;
    private VBox channelMemberPane;
    private VBox dmProfilePane;
    private VBox dmProfileContent;
    private SplitPane messageMemberSplit;
    private Label memberHeaderLabel;
    private StackPane userBarAvatarPane;
    private StackPane messageInputStack;
    private Label userBarNameLabel;
    private Label userBarStatusLabel;
    private Region userBarStatusIndicator;
    private Button userBarPresenceButton;
    private HBox composerBar;
    private HBox composerReplyBar;
    private FlowPane composerAttachmentPane;
    private Label composerReplyLabel;
    private Label composerReplySnippetLabel;
    private VBox composerBlockedPane;
    private Label composerBlockedLabel;
    private Button composerBlockedActionButton;
    private String activeHost = "127.0.0.1";
    private int activePort = 5555;
    private String selectedServerId;
    private String selectedChannelId;
    private String selectedDmUserId;
    private boolean dmHomeSelected = true;
    private boolean sidebarDmVisible = true;
    private boolean localTypingActive;
    private boolean suppressSelectionEvents;
    private int renderedMessageCount;
    private String unreadBoundaryChannelKey;
    private String unreadBoundaryDmUserId;
    private int unreadBoundaryCount;
    private boolean unreadBoundaryPending;
    private UserProfileDetails selectedDmProfile;
    private ReplyTarget composerReplyTarget;
    private EditingMessageTarget editingMessageTarget;
    private String editingMessageDraft = "";
    private String pendingEditFocusMessageId;
    private String pendingMessageScrollId;
    private MentionQuery activeMentionQuery;
    private int selectedMentionSuggestionIndex = -1;
    private final Map<String, UserProfileDetails> dmProfileCache = new HashMap<>();
    private final Map<String, Image> reactionEmojiCache = new ConcurrentHashMap<>();
    private final Map<String, Image> attachmentImageCache = new ConcurrentHashMap<>();
    private final Map<String, ObjectProperty<Image>> reactionEmojiProperties = new ConcurrentHashMap<>();
    private final Set<String> pendingEmojiDownloads = ConcurrentHashMap.newKeySet();
    private final ExecutorService emojiImageLoader = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "emoji-image-loader");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void start(Stage stage) {
        service.addListener(event -> Platform.runLater(() -> onClientEvent(event)));
        typingPause.setOnFinished(event -> stopLocalTyping());
        themeStylesheet = HelloApplication.class.getResource("theme.css").toExternalForm();

        configureMentionSuggestions();
        configureListRenderers();
        configureSelectionListeners();
        chatPageRoot = buildChatPage();
        welcomePageRoot = buildWelcomePage();
        loginPageRoot = buildLoginPage();
        registerPageRoot = buildRegisterPage();

        pageContainer = new StackPane();
        pageContainer.getStyleClass().add("page-container");
        switchPage(AppPage.WELCOME, false);

        Scene scene = new Scene(pageContainer, 1280, 760);
        scene.getStylesheets().add(themeStylesheet);
        stage.setTitle("Discord Lite");
        stage.setScene(scene);
        stage.show();
        installServerRailScrollbarFix();
    }

    @Override
    public void stop() {
        emojiImageLoader.shutdownNow();
        service.disconnect();
    }

    private BorderPane buildChatPage() {
        serverListView.getStyleClass().add("server-list");
        serverListView.setFocusTraversable(false);

        dmHomeButton = createRailIconButton("fas-comments", "Home (Direct Messages)");
        dmHomeButton.getStyleClass().add("dm-home-button");
        dmHomeButton.setOnAction(event -> selectDmHome());

        Button createServerRailButton = createRailActionButton("fas-plus", "Create server");
        createServerRailButton.setOnAction(event -> createServer());

        Button joinServerRailButton = createRailActionButton("fas-compass", "Join server");
        joinServerRailButton.setOnAction(event -> joinServer());

        VBox railActions = new VBox(8, createServerRailButton, joinServerRailButton);
        railActions.setAlignment(Pos.CENTER);
        railActions.getStyleClass().add("rail-actions");

        VBox serverPane = new VBox(10, dmHomeButton, serverListView, railActions);
        serverPane.setPadding(new Insets(10));
        serverPane.setPrefWidth(104);
        serverPane.setMinWidth(104);
        serverPane.getStyleClass().add("server-rail");
        VBox.setVgrow(serverListView, Priority.ALWAYS);

        contextHeaderLabel = new Label("DIRECT MESSAGES");
        contextHeaderLabel.getStyleClass().add("sidebar-heading");
        contextActionButton = createRailActionButton("fas-plus", "New direct message");
        contextActionButton.getStyleClass().add("context-action");
        contextActionButton.setOnAction(event -> {
            if (dmHomeSelected) {
                openDm();
            } else {
                createChannel();
            }
        });
        contextGroupActionButton = createRailActionButton("fas-folder-plus", "Create channel group");
        contextGroupActionButton.getStyleClass().add("context-action");
        contextGroupActionButton.setOnAction(event -> createChannelGroup());
        contextGroupActionButton.setVisible(false);
        contextGroupActionButton.setManaged(false);
        contextInviteButton = createRailActionButton("fas-user-plus", "Invite people");
        contextInviteButton.getStyleClass().add("context-action");
        contextInviteButton.setOnAction(event -> showServerInviteModal());
        contextInviteButton.setVisible(false);
        contextInviteButton.setManaged(false);
        contextSettingsButton = createRailActionButton("fas-cog", "Server settings");
        contextSettingsButton.getStyleClass().add("context-action");
        contextSettingsButton.setOnAction(event -> showServerSettingsModal());
        contextSettingsButton.setVisible(false);
        contextSettingsButton.setManaged(false);

        HBox contextHeader = new HBox(
            8,
            contextHeaderLabel,
            new Region(),
            contextSettingsButton,
            contextInviteButton,
            contextGroupActionButton,
            contextActionButton
        );
        contextHeader.getStyleClass().add("context-header");
        HBox.setHgrow(contextHeader.getChildren().get(1), Priority.ALWAYS);

        channelListView.getStyleClass().add("channel-list");
        dmListView.getStyleClass().add("dm-list");
        channelListView.setFocusTraversable(false);
        dmListView.setFocusTraversable(false);

        serverBannerPane = new StackPane();
        serverBannerPane.getStyleClass().add("server-banner");
        serverBannerPane.setVisible(false);
        serverBannerPane.setManaged(false);
        channelSectionPane = new VBox(10, serverBannerPane, channelListView);
        dmSectionPane = new VBox(dmListView);
        channelSectionPane.getStyleClass().add("sidebar-section");
        dmSectionPane.getStyleClass().add("sidebar-section");
        VBox.setVgrow(channelListView, Priority.ALWAYS);
        VBox.setVgrow(dmListView, Priority.ALWAYS);

        StackPane contextStack = new StackPane(channelSectionPane, dmSectionPane);
        contextStack.getStyleClass().add("context-stack");
        VBox.setVgrow(contextStack, Priority.ALWAYS);

        HBox userBar = buildUserBar();

        VBox conversationPane = new VBox(10, contextHeader, contextStack, userBar);
        conversationPane.setPadding(new Insets(10));
        conversationPane.setPrefWidth(300);
        conversationPane.getStyleClass().add("conversation-rail");

        Button sendButton = new Button("Send");
        sendButton.getStyleClass().addAll("toolbar-button", "button-brand");
        sendButton.setDefaultButton(true);
        sendButton.setOnAction(event -> sendMessage());

        Button attachButton = new Button();
        attachButton.setText(null);
        attachButton.setFocusTraversable(false);
        attachButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        attachButton.getStyleClass().add("composer-attach-button");
        attachButton.setGraphic(createIcon("fas-paperclip", "composer-attach-icon"));
        attachButton.setOnAction(event -> chooseComposerAttachments());
        applyTooltip(attachButton, "Attach files");

        messageInput.setPromptText("Type a message...");
        messageInput.getStyleClass().add("chat-input");
        messageInput.setOnAction(event -> sendMessage());
        messageInput.textProperty().addListener((obs, oldValue, newValue) -> {
            refreshComposerPreview(newValue);
            refreshMentionSuggestions();
            onComposerChanged(newValue);
        });
        messageInput.caretPositionProperty().addListener((obs, oldValue, newValue) -> refreshMentionSuggestions());
        messageInput.focusedProperty().addListener((obs, oldValue, focused) -> {
            refreshMessageInputStackState();
            if (!focused) {
                hideMentionSuggestions();
            } else {
                refreshMentionSuggestions();
            }
        });
        messageInput.addEventFilter(KeyEvent.KEY_PRESSED, this::handleComposerKeyPressed);

        messageInputPreview.getStyleClass().add("chat-input-preview");
        messageInputPreview.setLineSpacing(1);
        messageInputPreview.setMouseTransparent(true);
        messageInputPreview.setVisible(false);

        messageInputStack = new StackPane(messageInputPreview, messageInput);
        messageInputStack.getStyleClass().add("chat-input-stack");
        StackPane.setAlignment(messageInput, Pos.CENTER_LEFT);
        StackPane.setAlignment(messageInputPreview, Pos.CENTER_LEFT);
        messageInputPreview.prefWidthProperty().bind(Bindings.max(0.0, messageInputStack.widthProperty().subtract(24)));
        HBox.setHgrow(messageInputStack, Priority.ALWAYS);

        composerBar = new HBox(10, attachButton, messageInputStack, sendButton);
        composerBar.setPadding(new Insets(10));
        composerBar.setAlignment(Pos.CENTER_LEFT);
        composerBar.getStyleClass().add("chat-composer");
        refreshMessageInputStackState();
        refreshComposerPreview(messageInput.getText());

        composerAttachmentPane = new FlowPane(10, 10);
        composerAttachmentPane.getStyleClass().add("composer-attachments");
        composerAttachmentPane.setVisible(false);
        composerAttachmentPane.setManaged(false);
        refreshComposerAttachmentsUi();

        Label replyEyebrow = new Label("Replying to");
        replyEyebrow.getStyleClass().add("composer-reply-eyebrow");

        composerReplyLabel = new Label();
        composerReplyLabel.getStyleClass().add("composer-reply-label");

        composerReplySnippetLabel = new Label();
        composerReplySnippetLabel.getStyleClass().add("composer-reply-snippet");
        composerReplySnippetLabel.setWrapText(true);

        VBox replyMeta = new VBox(2, replyEyebrow, composerReplyLabel, composerReplySnippetLabel);
        replyMeta.getStyleClass().add("composer-reply-meta");
        HBox.setHgrow(replyMeta, Priority.ALWAYS);

        Button clearReplyButton = new Button();
        clearReplyButton.setText(null);
        clearReplyButton.setFocusTraversable(false);
        clearReplyButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        clearReplyButton.getStyleClass().add("composer-reply-close");
        clearReplyButton.setGraphic(createIcon("fas-times", "composer-reply-close-icon"));
        clearReplyButton.setOnAction(event -> clearComposerReply());

        composerReplyBar = new HBox(10, createIcon("fas-reply", "composer-reply-icon"), replyMeta, clearReplyButton);
        composerReplyBar.setAlignment(Pos.CENTER_LEFT);
        composerReplyBar.getStyleClass().add("composer-reply-bar");
        composerReplyBar.setVisible(false);
        composerReplyBar.setManaged(false);

        composerBlockedLabel = new Label("You cannot message this user right now.");
        composerBlockedLabel.getStyleClass().add("composer-blocked-label");
        composerBlockedLabel.setWrapText(true);

        composerBlockedActionButton = createActionChip("Unblock", "fas-user-check", "action-chip-primary");
        composerBlockedActionButton.setVisible(false);
        composerBlockedActionButton.setManaged(false);

        composerBlockedPane = new VBox(10, composerBlockedLabel, composerBlockedActionButton);
        composerBlockedPane.getStyleClass().add("composer-blocked-pane");
        composerBlockedPane.setVisible(false);
        composerBlockedPane.setManaged(false);

        Label titleLabel = new Label("Discord Lite (LAN)");
        titleLabel.getStyleClass().add("chat-title");
        channelLabel.getStyleClass().add("chat-subtitle");
        typingLabel.getStyleClass().add("typing-label");
        messageListView.getStyleClass().add("message-list");
        messageListView.setFocusTraversable(false);
        updateMessagePlaceholder();

        VBox messageHeader = new VBox(4, titleLabel, channelLabel, typingLabel);
        messageHeader.setPadding(new Insets(10, 10, 0, 10));
        messageHeader.getStyleClass().add("chat-header");

        BorderPane messagesPane = new BorderPane();
        messagesPane.setTop(messageHeader);
        messagesPane.setCenter(messageListView);
        VBox composerContainer = new VBox(composerReplyBar, composerAttachmentPane, composerBar, composerBlockedPane);
        composerContainer.getStyleClass().add("composer-container");
        messagesPane.setBottom(composerContainer);
        messagesPane.getStyleClass().add("chat-pane");

        memberHeaderLabel = new Label("MEMBERS");
        memberHeaderLabel.getStyleClass().add("member-heading");
        memberSearchField.getStyleClass().add("member-search");
        memberSearchField.setPromptText("Search members");
        memberSearchField.textProperty().addListener((obs, oldValue, newValue) -> applyMemberFilter());
        memberListView.getStyleClass().add("member-list");
        memberListView.setFocusTraversable(false);
        channelMemberPane = new VBox(8, memberHeaderLabel, memberSearchField, memberListView);
        channelMemberPane.getStyleClass().add("member-section");
        VBox.setVgrow(memberListView, Priority.ALWAYS);

        Label dmProfileHeading = new Label("PROFILE");
        dmProfileHeading.getStyleClass().add("member-heading");
        dmProfileContent = new VBox(14);
        dmProfileContent.getStyleClass().add("dm-profile-content");

        ScrollPane dmProfileScrollPane = new ScrollPane(dmProfileContent);
        dmProfileScrollPane.getStyleClass().add("dm-profile-scroll");
        dmProfileScrollPane.setFitToWidth(true);
        dmProfileScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        dmProfileScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(dmProfileScrollPane, Priority.ALWAYS);

        dmProfilePane = new VBox(10, dmProfileHeading, dmProfileScrollPane);
        dmProfilePane.getStyleClass().addAll("member-section", "dm-profile-pane");
        dmProfilePane.setVisible(false);
        dmProfilePane.setManaged(false);
        VBox.setVgrow(dmProfilePane, Priority.ALWAYS);

        memberPane = new VBox(channelMemberPane, dmProfilePane);
        memberPane.getStyleClass().add("member-pane");
        memberPane.setPrefWidth(280);
        memberPane.setMinWidth(220);
        VBox.setVgrow(channelMemberPane, Priority.ALWAYS);

        HBox leftColumn = new HBox(serverPane, conversationPane);
        leftColumn.getStyleClass().add("left-columns");
        HBox.setHgrow(conversationPane, Priority.ALWAYS);

        messageMemberSplit = new SplitPane(messagesPane, memberPane);
        messageMemberSplit.setDividerPositions(0.76);
        messageMemberSplit.getStyleClass().add("chat-content-split");

        SplitPane splitPane = new SplitPane(leftColumn, messageMemberSplit);
        splitPane.setDividerPositions(0.32);
        splitPane.getStyleClass().add("main-split");

        BorderPane root = new BorderPane();
        root.setCenter(splitPane);
        root.getStyleClass().add("app-root");

        updateDmHomeButtonState();
        updateContextSidebarMode();
        refreshMembers();
        refreshComposerState();
        return root;
    }

    private void installServerRailScrollbarFix() {
        Runnable hideScrollbars = () -> {
            if (serverListView.getScene() == null) {
                return;
            }
            serverListView.lookupAll(".scroll-bar").forEach(node -> {
                node.setManaged(false);
                node.setVisible(false);
                node.setMouseTransparent(true);
                node.setOpacity(0);
            });
        };
        serverListView.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(hideScrollbars));
        serverListView.widthProperty().addListener((obs, oldValue, newValue) -> Platform.runLater(hideScrollbars));
        Platform.runLater(hideScrollbars);
    }

    private Node buildLoginPage() {
        loginHostField = new TextField(activeHost);
        loginHostField.getStyleClass().add("auth-input");
        loginPortField = new TextField(Integer.toString(activePort));
        loginPortField.getStyleClass().add("auth-input");
        loginUsernameField = new TextField();
        loginUsernameField.getStyleClass().add("auth-input");
        loginUsernameField.setPromptText("Email or Username");
        loginPasswordField = new PasswordField();
        loginPasswordField.getStyleClass().add("auth-input");
        loginPasswordField.setPromptText("Password");
        loginErrorLabel = new Label();
        loginErrorLabel.getStyleClass().add("auth-error");
        loginErrorLabel.setManaged(false);
        loginErrorLabel.setVisible(false);

        Button loginButton = new Button("Login");
        loginButton.getStyleClass().addAll("auth-primary-button");
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(event -> loginFromPage());

        Button backButton = new Button("Back");
        backButton.getStyleClass().add("auth-back-button");
        backButton.setOnAction(event -> {
            clearAuthErrors();
            switchPage(AppPage.WELCOME, true);
        });

        Button registerLink = new Button("Need an account? Register");
        registerLink.getStyleClass().add("auth-link-button");
        registerLink.setOnAction(event -> {
            clearAuthErrors();
            copyLoginConnectionToRegister();
            switchPage(AppPage.REGISTER, true);
        });

        HBox authLinks = new HBox(12, backButton, new Region(), registerLink);
        authLinks.getStyleClass().add("auth-links");
        HBox.setHgrow(authLinks.getChildren().get(1), Priority.ALWAYS);

        GridPane form = new GridPane();
        form.getStyleClass().add("auth-form");
        form.setVgap(10);
        form.setHgap(12);
        form.add(new Label("HOST"), 0, 0);
        form.add(loginHostField, 0, 1);
        form.add(new Label("PORT"), 1, 0);
        form.add(loginPortField, 1, 1);
        form.add(new Label("USERNAME"), 0, 2, 2, 1);
        form.add(loginUsernameField, 0, 3, 2, 1);
        form.add(new Label("PASSWORD"), 0, 4, 2, 1);
        form.add(loginPasswordField, 0, 5, 2, 1);
        form.add(loginErrorLabel, 0, 6, 2, 1);
        form.add(loginButton, 0, 7, 2, 1);
        form.add(authLinks, 0, 8, 2, 1);

        VBox card = new VBox(
            14,
            createAuthTitle("Welcome back!", "We're so excited to see you again!"),
            form
        );
        card.getStyleClass().addAll("auth-card", "auth-form-card");
        card.setMaxWidth(460);

        VBox hero = createAuthHero(
            "RETURN TO YOUR SERVER",
            "Log in and drop back into the conversation.",
            "Reconnect to your LAN server, pick up unread messages, and jump between rooms instantly.",
            "Live rooms",
            "Direct messages",
            "Presence + typing"
        );
        return buildAuthScene(hero, card, false);
    }

    private Node buildRegisterPage() {
        registerHostField = new TextField(activeHost);
        registerHostField.getStyleClass().add("auth-input");
        registerPortField = new TextField(Integer.toString(activePort));
        registerPortField.getStyleClass().add("auth-input");
        registerNameField = new TextField();
        registerNameField.getStyleClass().add("auth-input");
        registerNameField.setPromptText("Display Name");
        registerUsernameField = new TextField();
        registerUsernameField.getStyleClass().add("auth-input");
        registerUsernameField.setPromptText("Username");
        registerUsernameHintLabel = new Label("Usernames are unique and cannot be changed later.");
        registerUsernameHintLabel.getStyleClass().add("auth-hint");
        registerAvatarPreview = new StackPane();
        registerAvatarPreview.getStyleClass().add("register-avatar-preview");
        refreshRegisterAvatarPreview();
        registerNameField.textProperty().addListener((obs, oldValue, newValue) -> refreshRegisterAvatarPreview());
        registerPasswordField = new PasswordField();
        registerPasswordField.getStyleClass().add("auth-input");
        registerPasswordField.setPromptText("Password");
        registerConfirmPasswordField = new PasswordField();
        registerConfirmPasswordField.getStyleClass().add("auth-input");
        registerConfirmPasswordField.setPromptText("Confirm Password");
        registerErrorLabel = new Label();
        registerErrorLabel.getStyleClass().add("auth-error");
        registerErrorLabel.setManaged(false);
        registerErrorLabel.setVisible(false);

        Button createButton = new Button("Create Account");
        createButton.getStyleClass().add("auth-primary-button");
        createButton.setDefaultButton(true);
        createButton.setOnAction(event -> registerFromPage());

        Button backButton = new Button("Back");
        backButton.getStyleClass().add("auth-back-button");
        backButton.setOnAction(event -> {
            clearAuthErrors();
            switchPage(AppPage.WELCOME, true);
        });

        Button suggestUsernameButton = new Button("Suggest");
        suggestUsernameButton.getStyleClass().add("auth-link-button");
        suggestUsernameButton.setOnAction(event -> suggestUsernameFromRegister());

        Button chooseAvatarButton = new Button("Choose Picture");
        chooseAvatarButton.getStyleClass().add("auth-link-button");
        chooseAvatarButton.setOnAction(event -> {
            try {
                String nextAvatar = chooseImageBase64("Choose Profile Picture", PROFILE_IMAGE_MAX_BYTES, "Profile picture");
                if (nextAvatar != null) {
                    registerSelectedAvatarBase64 = nextAvatar;
                    refreshRegisterAvatarPreview();
                }
            } catch (IllegalArgumentException | IOException ex) {
                setAuthError(registerErrorLabel, rootMessage(ex));
            }
        });

        Button removeAvatarButton = new Button("Remove");
        removeAvatarButton.getStyleClass().add("auth-link-button");
        removeAvatarButton.setOnAction(event -> {
            registerSelectedAvatarBase64 = null;
            refreshRegisterAvatarPreview();
        });

        Button loginLink = new Button("Already have an account? Login");
        loginLink.getStyleClass().add("auth-link-button");
        loginLink.setOnAction(event -> {
            clearAuthErrors();
            copyRegisterConnectionToLogin();
            switchPage(AppPage.LOGIN, true);
        });

        HBox usernameRow = new HBox(8, registerUsernameField, suggestUsernameButton);
        HBox.setHgrow(registerUsernameField, Priority.ALWAYS);
        HBox avatarRow = new HBox(12, registerAvatarPreview, new VBox(6, chooseAvatarButton, removeAvatarButton));
        avatarRow.setAlignment(Pos.CENTER_LEFT);

        HBox authLinks = new HBox(12, backButton, new Region(), loginLink);
        authLinks.getStyleClass().add("auth-links");
        HBox.setHgrow(authLinks.getChildren().get(1), Priority.ALWAYS);

        GridPane form = new GridPane();
        form.getStyleClass().add("auth-form");
        form.setVgap(10);
        form.setHgap(12);
        form.add(new Label("HOST"), 0, 0);
        form.add(registerHostField, 0, 1);
        form.add(new Label("PORT"), 1, 0);
        form.add(registerPortField, 1, 1);
        form.add(new Label("DISPLAY NAME"), 0, 2, 2, 1);
        form.add(registerNameField, 0, 3, 2, 1);
        form.add(new Label("USERNAME"), 0, 4, 2, 1);
        form.add(usernameRow, 0, 5, 2, 1);
        form.add(registerUsernameHintLabel, 0, 6, 2, 1);
        form.add(new Label("PROFILE PICTURE (OPTIONAL)"), 0, 7, 2, 1);
        form.add(avatarRow, 0, 8, 2, 1);
        form.add(new Label("PASSWORD"), 0, 9, 2, 1);
        form.add(registerPasswordField, 0, 10, 2, 1);
        form.add(new Label("CONFIRM PASSWORD"), 0, 11, 2, 1);
        form.add(registerConfirmPasswordField, 0, 12, 2, 1);
        form.add(registerErrorLabel, 0, 13, 2, 1);
        form.add(createButton, 0, 14, 2, 1);
        form.add(authLinks, 0, 15, 2, 1);

        VBox card = new VBox(
            14,
            createAuthTitle("Create your account", "Join your server and start chatting."),
            form
        );
        card.getStyleClass().addAll("auth-card", "auth-form-card");
        card.setMaxWidth(460);

        VBox hero = createAuthHero(
            "START SOMETHING NEW",
            "Create a profile and join your first community.",
            "Choose your display name, lock in a unique username, add an optional avatar, and connect to your server in one flow.",
            "Unique usernames",
            "Invite-based servers",
            "Dark, focused UI"
        );
        return buildAuthScene(hero, card, false);
    }

    private Node buildWelcomePage() {
        Label eyebrow = new Label("DISCORD LITE FOR LAN");
        eyebrow.getStyleClass().add("welcome-eyebrow");

        Label title = new Label("A calmer home\nfor your communities.");
        title.getStyleClass().add("welcome-title");

        Label subtitle = new Label(
            "Private servers, direct messages, presence, and typing indicators on your local network with a Discord-inspired desktop experience."
        );
        subtitle.getStyleClass().add("welcome-subtitle");
        subtitle.setWrapText(true);

        HBox chips = new HBox(
            10,
            createAuthChip("Realtime channels"),
            createAuthChip("Invite codes"),
            createAuthChip("Local-first")
        );
        chips.getStyleClass().add("welcome-chip-row");

        VBox signalCard = createWelcomeInfoCard("Server Spaces", "Create rooms, organize channels, and keep context visible.");
        VBox directCard = createWelcomeInfoCard("Direct Messages", "Reach anyone instantly with unread badges and status indicators.");
        VBox presenceCard = createWelcomeInfoCard("Presence Layer", "See who is active, idle, busy, or invisible at a glance.");
        HBox infoCards = new HBox(14, signalCard, directCard, presenceCard);
        infoCards.getStyleClass().add("welcome-info-row");

        VBox hero = new VBox(16, eyebrow, title, subtitle, chips, infoCards);
        hero.getStyleClass().addAll("auth-hero", "welcome-hero");

        Label cardTitle = new Label("Welcome");
        cardTitle.getStyleClass().add("welcome-card-title");
        Label cardSubtitle = new Label("Launch into your space with a cleaner first step.");
        cardSubtitle.getStyleClass().add("welcome-card-subtitle");
        cardSubtitle.setWrapText(true);

        Button loginButton = new Button("Log In");
        loginButton.getStyleClass().addAll("auth-primary-button", "welcome-cta-button");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(event -> {
            clearAuthErrors();
            switchPage(AppPage.LOGIN, true);
        });

        Button registerButton = new Button("Create Account");
        registerButton.getStyleClass().addAll("auth-secondary-button", "welcome-cta-button");
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setOnAction(event -> {
            clearAuthErrors();
            switchPage(AppPage.REGISTER, true);
        });

        Label footnote = new Label("Connect to your own LAN server and keep the whole experience local.");
        footnote.getStyleClass().add("welcome-footnote");
        footnote.setWrapText(true);

        VBox card = new VBox(14, cardTitle, cardSubtitle, loginButton, registerButton, footnote);
        card.getStyleClass().addAll("auth-card", "welcome-card");
        card.setMaxWidth(360);

        return buildAuthScene(hero, card, true);
    }

    private VBox createAuthTitle(String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("auth-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("auth-subtitle");
        VBox box = new VBox(4, titleLabel, subtitleLabel);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private VBox createAuthHero(String eyebrowText, String titleText, String subtitleText, String... chips) {
        Label eyebrow = new Label(eyebrowText);
        eyebrow.getStyleClass().add("auth-eyebrow");

        Label title = new Label(titleText);
        title.getStyleClass().add("auth-hero-title");
        title.setWrapText(true);

        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().add("auth-hero-subtitle");
        subtitle.setWrapText(true);

        HBox chipRow = new HBox(10);
        chipRow.getStyleClass().add("auth-chip-row");
        for (String chip : chips) {
            chipRow.getChildren().add(createAuthChip(chip));
        }

        VBox hero = new VBox(16, eyebrow, title, subtitle, chipRow);
        hero.getStyleClass().add("auth-hero");
        return hero;
    }

    private Label createAuthChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("auth-chip");
        return chip;
    }

    private VBox createWelcomeInfoCard(String title, String body) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("welcome-info-title");
        Label bodyLabel = new Label(body);
        bodyLabel.getStyleClass().add("welcome-info-body");
        bodyLabel.setWrapText(true);
        VBox card = new VBox(8, titleLabel, bodyLabel);
        card.getStyleClass().add("welcome-info-card");
        return card;
    }

    private StackPane buildAuthScene(Node hero, Node card, boolean welcomeScene) {
        Region mesh = new Region();
        mesh.getStyleClass().add("auth-mesh");

        Region glowPrimary = new Region();
        glowPrimary.getStyleClass().addAll("auth-glow", "auth-glow-primary");
        StackPane.setAlignment(glowPrimary, Pos.TOP_LEFT);

        Region glowSecondary = new Region();
        glowSecondary.getStyleClass().addAll("auth-glow", "auth-glow-secondary");
        StackPane.setAlignment(glowSecondary, Pos.TOP_RIGHT);

        Region glowTertiary = new Region();
        glowTertiary.getStyleClass().addAll("auth-glow", "auth-glow-tertiary");
        StackPane.setAlignment(glowTertiary, Pos.BOTTOM_CENTER);

        Region horizon = new Region();
        horizon.getStyleClass().add("auth-horizon");
        StackPane.setAlignment(horizon, Pos.BOTTOM_CENTER);

        HBox shell = new HBox(42, hero, card);
        shell.setAlignment(Pos.CENTER);
        shell.getStyleClass().addAll("auth-shell", welcomeScene ? "welcome-shell" : "auth-shell-compact");
        HBox.setHgrow(hero, Priority.ALWAYS);

        StackPane root = new StackPane(mesh, glowPrimary, glowSecondary, glowTertiary, horizon, shell);
        root.getStyleClass().add("auth-root");
        return root;
    }

    private void switchPage(AppPage targetPage, boolean animated) {
        Node target = switch (targetPage) {
            case WELCOME -> welcomePageRoot;
            case LOGIN -> loginPageRoot;
            case REGISTER -> registerPageRoot;
            case CHAT -> chatPageRoot;
        };

        if (pageContainer.getChildren().isEmpty()) {
            pageContainer.getChildren().setAll(target);
        } else if (animated) {
            Node previous = pageContainer.getChildren().get(0);
            FadeTransition fadeOut = new FadeTransition(Duration.millis(120), previous);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                pageContainer.getChildren().setAll(target);
                target.setOpacity(0.0);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(150), target);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            fadeOut.play();
        } else {
            pageContainer.getChildren().setAll(target);
        }

        currentPage = targetPage;
        if (targetPage == AppPage.CHAT) {
            if (selectedServerId == null) {
                dmHomeSelected = true;
            }
            refreshSession();
            refreshServers();
            refreshChannels();
            if (selectedServerId != null && !dmHomeSelected) {
                selectFirstChannelIfNeeded();
            }
            refreshDms();
            refreshMembers();
            refreshMessages();
            refreshHeader();
        }
    }

    private void configureListRenderers() {
        serverListView.setCellFactory(ignored -> new ListCell<>() {
            private final Region activePill = new Region();
            private final StackPane wrapper = new StackPane();
            private final HBox row = new HBox(8, activePill, wrapper);
            private final Region unreadDot = new Region();

            {
                activePill.getStyleClass().add("server-active-pill");
                wrapper.getStyleClass().add("server-avatar-wrap");
                unreadDot.getStyleClass().add("rail-unread-dot");
                unreadDot.setVisible(false);
                unreadDot.setManaged(false);
                StackPane.setAlignment(unreadDot, Pos.TOP_RIGHT);
                StackPane.setMargin(unreadDot, new Insets(4, 4, 0, 0));
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("server-row");

                selectedProperty().addListener((obs, oldValue, newValue) ->
                    animateServerTabState(wrapper, activePill, newValue, isHover())
                );
                hoverProperty().addListener((obs, oldValue, newValue) ->
                    animateServerTabState(wrapper, activePill, isSelected(), newValue)
                );
            }

            @Override
            protected void updateItem(WorkspaceServer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                populateServerIconWrapper(wrapper, item, "server-avatar", 60, 18);
                int unread = service.unreadCountServer(item.getId());
                if (unread > 0) {
                    if (!wrapper.getChildren().contains(unreadDot)) {
                        wrapper.getChildren().add(unreadDot);
                    }
                    unreadDot.setVisible(true);
                    unreadDot.setManaged(true);
                } else {
                    wrapper.getChildren().remove(unreadDot);
                    unreadDot.setVisible(false);
                    unreadDot.setManaged(false);
                }
                setText(null);
                setGraphic(row);
                setAlignment(Pos.CENTER_LEFT);
                setTooltip(new Tooltip(item.getName() + "\nInvite code: " + inviteCodeFor(item)));
                animateServerTabState(wrapper, activePill, isSelected(), isHover());
            }
        });

        channelListView.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                if (item instanceof ChannelGroupHeaderRow headerRow) {
                    Label caret = new Label(headerRow.collapsed() ? "▸" : "▾");
                    caret.getStyleClass().add("channel-group-caret");

                    Label name = new Label(headerRow.group().getName().toUpperCase());
                    name.getStyleClass().add("channel-group-title");

                    Label count = new Label(Integer.toString(headerRow.channelCount()));
                    count.getStyleClass().add("channel-group-count");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    HBox row = new HBox(8, caret, name, spacer, count);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("channel-group-row");
                    setText(null);
                    setGraphic(row);
                    return;
                }

                if (item instanceof Channel channel) {
                    int unread = service.unreadCountChannel(channel.getServerId(), channel.getId());

                    Region activePill = new Region();
                    activePill.getStyleClass().add("channel-active-pill");

                    Label hash = new Label("#");
                    hash.getStyleClass().add("channel-prefix");

                    Label name = new Label(channel.getName());
                    name.getStyleClass().add("channel-name");
                    if (unread > 0) {
                        name.getStyleClass().add("channel-name-unread");
                    }

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    HBox content = new HBox(8, hash, name, spacer);
                    content.setAlignment(Pos.CENTER_LEFT);
                    content.getStyleClass().add("channel-row");
                    if (unread > 0) {
                        content.getStyleClass().add("channel-row-unread");
                    }

                    if (unread > 0) {
                        content.getChildren().add(createUnreadBadge(unread));
                    }

                    HBox.setHgrow(content, Priority.ALWAYS);
                    HBox row = new HBox(8, activePill, content);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("channel-row-shell");

                    setText(null);
                    setGraphic(row);
                    return;
                }

                setText(null);
                setGraphic(new Label(item.toString()));
            }
        });

        dmListView.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(UserSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                int unread = service.unreadCountDm(item.getId());

                Label presence = new Label();
                presence.getStyleClass().addAll("presence-dot", presenceStyleClass(item));

                StackPane avatarWrap = new StackPane(createAvatarGraphic(item, "dm-avatar", 40), presence);
                avatarWrap.getStyleClass().add("dm-avatar-wrap");
                StackPane.setAlignment(presence, Pos.BOTTOM_RIGHT);
                StackPane.setMargin(presence, new Insets(0, -2, -2, 0));

                Label name = new Label(displayName(item));
                name.getStyleClass().add("dm-name");
                if (unread > 0) {
                    name.getStyleClass().add("dm-name-unread");
                }
                if (!item.isOnline()) {
                    name.getStyleClass().add("dm-name-offline");
                    avatarWrap.getStyleClass().add("dm-avatar-wrap-offline");
                }

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox row = new HBox(10, avatarWrap, name, spacer);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("dm-row");
                if (!item.isOnline()) {
                    row.getStyleClass().add("dm-row-offline");
                }

                if (unread > 0) {
                    row.getChildren().add(createUnreadBadge(unread));
                }

                setText(null);
                setGraphic(row);
            }
        });

        memberListView.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }

                if (item instanceof MemberSectionHeader header) {
                    setText(null);
                    setGraphic(createMemberSectionRow(header));
                    setTooltip(null);
                    return;
                }

                if (!(item instanceof UserSummary user)) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }

                Label presence = new Label();
                presence.getStyleClass().addAll("presence-dot", presenceStyleClass(user));

                StackPane avatarWrap = new StackPane(createAvatarGraphic(user, "member-avatar", 36), presence);
                avatarWrap.getStyleClass().add("member-avatar-wrap");
                StackPane.setAlignment(presence, Pos.BOTTOM_RIGHT);

                WorkspaceServer server = selectedServer().orElse(null);
                Role memberRole = server == null ? Role.MEMBER : server.roleOf(user.getId());

                Label name = new Label(displayName(user));
                name.getStyleClass().add("member-name");
                if (!user.isOnline()) {
                    name.getStyleClass().add("member-name-offline");
                    avatarWrap.getStyleClass().add("member-avatar-wrap-offline");
                }

                HBox nameRow = new HBox(6);
                nameRow.setAlignment(Pos.CENTER_LEFT);
                nameRow.getStyleClass().add("member-name-row");
                nameRow.getChildren().add(name);
                if (memberRole == Role.ADMIN || memberRole == Role.OWNER) {
                    String roleLabel = serverRoleLabel(server, user.getId());
                    FontIcon crown = createIcon("fas-crown", "member-role-crown");
                    if (!user.isOnline()) {
                        crown.getStyleClass().add("member-role-crown-offline");
                    }
                    Tooltip.install(crown, new Tooltip(roleLabel));
                    nameRow.getChildren().add(crown);
                }

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                boolean canMessage =
                    service.currentUser()
                        .map(currentUser -> !currentUser.getId().equals(user.getId()))
                        .orElse(false);

                HBox row = canMessage
                    ? new HBox(
                        10,
                        avatarWrap,
                        nameRow,
                        spacer,
                        createMemberDmButton(user)
                    )
                    : new HBox(10, avatarWrap, nameRow, spacer);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("member-row");
                if (!user.isOnline()) {
                    row.getStyleClass().add("member-row-offline");
                }

                setText(null);
                setGraphic(row);
                String tooltipText = displayName(user) + " (" + statusLabelFor(user);
                if (memberRole != Role.MEMBER) {
                    tooltipText += " • " + serverRoleLabel(server, user.getId());
                }
                tooltipText += ")";
                setTooltip(new Tooltip(tooltipText));
            }
        });

        messageListView.setCellFactory(ignored -> new ListCell<>() {
            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                if (item instanceof ChatMessage message) {
                    Node row = createChannelMessageRow(message);
                    setText(null);
                    setGraphic(row);
                    animateMessageAppearance(row, getIndex(), messageListView.getItems().size());
                    return;
                }

                if (item instanceof ConversationDateDivider divider) {
                    setText(null);
                    setGraphic(createDateDividerRow(divider.label()));
                    return;
                }

                if (item instanceof ConversationUnreadDivider divider) {
                    setText(null);
                    setGraphic(createUnreadDividerRow(divider.label()));
                    return;
                }

                if (item instanceof ConversationChannelIntro intro) {
                    setText(null);
                    setGraphic(createChannelIntroRow(intro.channel(), intro.server()));
                    return;
                }

                if (item instanceof ConversationProfileIntro intro) {
                    Node row = createDmIntroRow(intro.profile());
                    setText(null);
                    setGraphic(row);
                    return;
                }

                if (item instanceof DirectMessage message) {
                    Node row = createDirectMessageRow(message);
                    setText(null);
                    setGraphic(row);
                    animateMessageAppearance(row, getIndex(), messageListView.getItems().size());
                    return;
                }

                setText(null);
                setGraphic(new Label(item.toString()));
            }
        });
    }

    private Button createMemberDmButton(UserSummary item) {
        Button dmButton = createRailActionButton("fas-comment-dots", "Message " + displayName(item));
        dmButton.getStyleClass().add("member-quick-action");
        dmButton.setOnAction(event -> startDmWithUser(item));
        return dmButton;
    }

    private Button createRailActionButton(String iconLiteral, String tooltipText) {
        Button button = new Button();
        button.setText(null);
        button.setGraphic(createIcon(iconLiteral, "rail-action-icon"));
        button.setFocusTraversable(false);
        button.getStyleClass().add("rail-action-button");
        applyTooltip(button, tooltipText);
        return button;
    }

    private Button createRailIconButton(String iconLiteral, String tooltipText) {
        Region activePill = new Region();
        activePill.getStyleClass().add("server-active-pill");

        Region unreadDot = new Region();
        unreadDot.getStyleClass().add("rail-unread-dot");
        unreadDot.setVisible(false);
        unreadDot.setManaged(false);

        StackPane iconWrap = new StackPane(createIcon(iconLiteral, "server-avatar-icon"), unreadDot);
        iconWrap.getStyleClass().add("server-avatar-wrap");
        StackPane.setAlignment(unreadDot, Pos.TOP_RIGHT);
        StackPane.setMargin(unreadDot, new Insets(4, 4, 0, 0));

        HBox row = new HBox(8, activePill, iconWrap);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("server-row");

        Button button = new Button();
        button.setGraphic(row);
        button.setText(null);
        button.setFocusTraversable(false);
        button.getStyleClass().add("server-icon-button");
        button.getProperties().put("activePill", activePill);
        button.getProperties().put("tileWrapper", iconWrap);
        button.getProperties().put("unreadDot", unreadDot);
        button.hoverProperty().addListener((obs, oldValue, newValue) -> {
            boolean selected = Boolean.TRUE.equals(button.getProperties().get("selected"));
            animateServerTabState(iconWrap, activePill, selected, newValue);
        });
        applyTooltip(button, tooltipText);
        return button;
    }

    private FontIcon createIcon(String literal, String styleClass) {
        FontIcon icon = new FontIcon(literal);
        icon.getStyleClass().add("icon-glyph");
        if (styleClass != null && !styleClass.isBlank()) {
            icon.getStyleClass().add(styleClass);
        }
        return icon;
    }

    private void applyTooltip(Button button, String tooltipText) {
        if (tooltipText == null || tooltipText.isBlank()) {
            button.setTooltip(null);
            button.setAccessibleText(null);
            return;
        }
        button.setTooltip(new Tooltip(tooltipText));
        button.setAccessibleText(tooltipText);
    }

    private void updateRailUnreadDot(Button button, boolean visible) {
        if (button == null) {
            return;
        }
        Object unreadDot = button.getProperties().get("unreadDot");
        if (unreadDot instanceof Region dot) {
            dot.setVisible(visible);
            dot.setManaged(visible);
        }
    }

    private HBox buildUserBar() {
        userBarAvatarPane = new StackPane();
        userBarAvatarPane.getStyleClass().add("user-bar-avatar-wrap");
        userBarStatusIndicator = new Region();
        userBarStatusIndicator.getStyleClass().addAll("user-bar-status-indicator", "user-bar-status-invisible");
        StackPane.setAlignment(userBarStatusIndicator, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(userBarStatusIndicator, new Insets(0, -1, -1, 0));
        refreshUserBarAvatar(null);

        userBarNameLabel = new Label("Guest");
        userBarNameLabel.getStyleClass().add("user-bar-name");

        userBarStatusLabel = new Label("Offline");
        userBarStatusLabel.getStyleClass().add("user-bar-status");

        VBox identity = new VBox(2, userBarNameLabel, userBarStatusLabel);
        identity.getStyleClass().add("user-bar-identity");

        HBox identityGroup = new HBox(10, userBarAvatarPane, identity);
        identityGroup.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(identityGroup, Priority.ALWAYS);

        userBarPresenceButton = createRailActionButton("fas-circle", "Quick status");
        userBarPresenceButton.getStyleClass().addAll("user-bar-action", "user-bar-presence-button");
        userBarPresenceButton.setOnAction(event -> showStatusPickerModal());

        Button editProfileButton = createRailActionButton("fas-cog", "Profile settings");
        editProfileButton.getStyleClass().add("user-bar-action");
        editProfileButton.setOnAction(event -> showEditProfileModal());

        Button logoutButton = createRailActionButton("fas-sign-out-alt", "Logout");
        logoutButton.getStyleClass().add("user-bar-action");
        logoutButton.setOnAction(event -> logout());

        HBox bar = new HBox(8, identityGroup, userBarPresenceButton, editProfileButton, logoutButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("user-bar");
        return bar;
    }

    private void updateMessagePlaceholder() {
        if (messageListView == null) {
            return;
        }
        if (activeMode == ConversationMode.NONE) {
            boolean dmContext = dmHomeSelected || sidebarDmVisible || selectedServerId == null;
            messageListView.setPlaceholder(createConversationEmptyState(dmContext));
            return;
        }
        messageListView.setPlaceholder(new Region());
    }

    private Node createConversationEmptyState(boolean dmContext) {
        Label title = new Label(dmContext ? "No DM selected" : "No channel selected");
        title.getStyleClass().add("conversation-empty-title");

        StackPane shell = new StackPane(title);
        shell.getStyleClass().add("conversation-empty-shell");
        return shell;
    }

    private void refreshMessageInputStackState() {
        if (messageInputStack == null || messageInput == null) {
            return;
        }
        messageInputStack.getStyleClass().remove("chat-input-stack-focused");
        if (messageInput.isFocused()) {
            messageInputStack.getStyleClass().add("chat-input-stack-focused");
        }
    }

    private Optional<Channel> selectedChannel() {
        if (selectedChannelId == null) {
            return Optional.empty();
        }
        return channelListView.getItems().stream()
            .filter(Channel.class::isInstance)
            .map(Channel.class::cast)
            .filter(channel -> selectedChannelId.equals(channel.getId()))
            .findFirst();
    }

    private String currentConversationKey() {
        if (activeMode == ConversationMode.CHANNEL && selectedServerId != null && selectedChannelId != null) {
            return "channel:" + selectedServerId + ":" + selectedChannelId;
        }
        if (activeMode == ConversationMode.DM && selectedDmUserId != null) {
            return "dm:" + selectedDmUserId;
        }
        return null;
    }

    private boolean isOwnMessage(String senderUserId) {
        String currentUserId = service.currentUser().map(UserSummary::getId).orElse(null);
        return currentUserId != null && currentUserId.equals(senderUserId);
    }

    private boolean isEditingMessage(String messageId) {
        return editingMessageTarget != null &&
            Objects.equals(editingMessageTarget.conversationKey(), currentConversationKey()) &&
            Objects.equals(editingMessageTarget.messageId(), messageId);
    }

    private void startEditingChannelMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        beginMessageEdit(message.getId(), message.getContent());
    }

    private void startEditingDmMessage(DirectMessage message) {
        if (message == null) {
            return;
        }
        beginMessageEdit(message.getId(), message.getContent());
    }

    private void beginMessageEdit(String messageId, String content) {
        String conversationKey = currentConversationKey();
        if (conversationKey == null || messageId == null || messageId.isBlank()) {
            return;
        }
        editingMessageTarget = new EditingMessageTarget(conversationKey, messageId);
        editingMessageDraft = content == null ? "" : content;
        pendingEditFocusMessageId = messageId;
        pendingMessageScrollId = messageId;
        hideMentionSuggestions();
        messageListView.refresh();
    }

    private void cancelMessageEdit() {
        clearMessageEditState(true);
    }

    private void clearMessageEditState(boolean refreshRows) {
        editingMessageTarget = null;
        editingMessageDraft = "";
        pendingEditFocusMessageId = null;
        if (refreshRows) {
            messageListView.refresh();
        }
    }

    private void syncMessageEditState(List<?> messages) {
        if (editingMessageTarget == null) {
            return;
        }
        if (!Objects.equals(editingMessageTarget.conversationKey(), currentConversationKey())) {
            clearMessageEditState(false);
            return;
        }
        boolean exists = messages.stream()
            .map(this::messageIdForItem)
            .filter(Objects::nonNull)
            .anyMatch(messageId -> messageId.equals(editingMessageTarget.messageId()));
        if (!exists) {
            clearMessageEditState(false);
        }
    }

    private String scrollAnchorMessageId() {
        if (pendingMessageScrollId != null && !pendingMessageScrollId.isBlank()) {
            return pendingMessageScrollId;
        }
        if (editingMessageTarget != null && Objects.equals(editingMessageTarget.conversationKey(), currentConversationKey())) {
            return editingMessageTarget.messageId();
        }
        return null;
    }

    private int messageIndexForId(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return -1;
        }
        List<Object> items = messageListView.getItems();
        for (int index = 0; index < items.size(); index++) {
            if (messageId.equals(messageIdForItem(items.get(index)))) {
                return index;
            }
        }
        return -1;
    }

    private String neighborMessageId(String messageId) {
        int index = messageIndexForId(messageId);
        if (index < 0) {
            return null;
        }
        List<Object> items = messageListView.getItems();
        for (int candidate = index - 1; candidate >= 0; candidate--) {
            String candidateId = messageIdForItem(items.get(candidate));
            if (candidateId != null) {
                return candidateId;
            }
        }
        for (int candidate = index + 1; candidate < items.size(); candidate++) {
            String candidateId = messageIdForItem(items.get(candidate));
            if (candidateId != null) {
                return candidateId;
            }
        }
        return null;
    }

    private String messageIdForItem(Object item) {
        if (item instanceof ChatMessage message) {
            return message.getId();
        }
        if (item instanceof DirectMessage message) {
            return message.getId();
        }
        return null;
    }

    private void setComposerReply(ReplyTarget target) {
        composerReplyTarget = target;
        refreshComposerReplyUi();
        if (messageInput != null) {
            messageInput.requestFocus();
        }
    }

    private void clearComposerReply() {
        composerReplyTarget = null;
        refreshComposerReplyUi();
    }

    private void refreshComposerReplyUi() {
        if (composerReplyBar == null || composerReplyLabel == null || composerReplySnippetLabel == null) {
            return;
        }

        String conversationKey = currentConversationKey();
        if (composerReplyTarget != null && !composerReplyTarget.conversationKey().equals(conversationKey)) {
            composerReplyTarget = null;
        }

        boolean visible = composerReplyTarget != null;
        composerReplyBar.setVisible(visible);
        composerReplyBar.setManaged(visible);
        composerReplyLabel.setText(visible ? composerReplyTarget.senderName() : "");
        composerReplySnippetLabel.setText(visible ? composerReplyTarget.previewText() : "");
    }

    private void chooseComposerAttachments() {
        if (pageContainer == null || pageContainer.getScene() == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Attach Files");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Files", "*.*"),
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
            new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.txt", "*.md", "*.zip")
        );
        List<java.io.File> selectedFiles = chooser.showOpenMultipleDialog(pageContainer.getScene().getWindow());
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            return;
        }

        try {
            addComposerAttachments(selectedFiles);
        } catch (IllegalArgumentException | IOException ex) {
            showError(rootMessage(ex));
        }
    }

    private void addComposerAttachments(List<java.io.File> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return;
        }

        String conversationKey = currentConversationKey();
        if (conversationKey == null) {
            throw new IllegalArgumentException("Select a channel or DM before attaching files.");
        }

        List<MessageAttachment> nextAttachments =
            Objects.equals(conversationKey, composerAttachmentConversationKey)
                ? new ArrayList<>(pendingComposerAttachments)
                : new ArrayList<>();
        long totalBytes = nextAttachments.stream().mapToLong(MessageAttachment::getSizeBytes).sum();
        for (java.io.File file : files) {
            if (file == null) {
                continue;
            }
            if (nextAttachments.size() >= MESSAGE_ATTACHMENT_MAX_COUNT) {
                throw new IllegalArgumentException(
                    "You can attach up to " + MESSAGE_ATTACHMENT_MAX_COUNT + " files per message."
                );
            }

            MessageAttachment attachment = buildAttachmentFromFile(file.toPath());
            totalBytes += attachment.getSizeBytes();
            if (totalBytes > MESSAGE_ATTACHMENT_TOTAL_MAX_BYTES) {
                throw new IllegalArgumentException("Selected files exceed the maximum total attachment size.");
            }
            nextAttachments.add(attachment);
        }

        pendingComposerAttachments.clear();
        pendingComposerAttachments.addAll(nextAttachments);
        composerAttachmentConversationKey = conversationKey;
        refreshComposerAttachmentsUi();
        refreshComposerState();
    }

    private MessageAttachment buildAttachmentFromFile(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Selected file is invalid.");
        }

        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length == 0) {
            throw new IllegalArgumentException(file.getFileName() + " is empty.");
        }
        if (bytes.length > MESSAGE_ATTACHMENT_MAX_BYTES) {
            throw new IllegalArgumentException(
                file.getFileName() + " exceeds the " + String.format(Locale.ROOT, "%.1f", MESSAGE_ATTACHMENT_MAX_BYTES / 1_000_000.0)
                    + " MB attachment limit."
            );
        }

        String fileName = file.getFileName() == null ? "attachment" : file.getFileName().toString();
        String contentType = probeAttachmentContentType(file);
        return MessageAttachment.create(fileName, contentType, bytes.length, Base64.getEncoder().encodeToString(bytes));
    }

    private void clearComposerAttachments() {
        composerAttachmentConversationKey = null;
        pendingComposerAttachments.clear();
        refreshComposerAttachmentsUi();
    }

    private void refreshComposerAttachmentsUi() {
        if (composerAttachmentPane == null) {
            return;
        }

        composerAttachmentPane.getChildren().clear();
        for (MessageAttachment attachment : pendingComposerAttachments) {
            composerAttachmentPane.getChildren().add(createComposerAttachmentCard(attachment));
        }
        boolean visible = !pendingComposerAttachments.isEmpty();
        composerAttachmentPane.setVisible(visible);
        composerAttachmentPane.setManaged(visible);
    }

    private Node createComposerAttachmentCard(MessageAttachment attachment) {
        VBox card = new VBox(8);
        card.getStyleClass().add("composer-attachment-card");

        Region preview = new Region();
        preview.setManaged(false);
        preview.setVisible(false);
        if (isImageAttachment(attachment)) {
            Image image = decodeAttachmentImage(attachment);
            if (image != null) {
                ImageView previewView = new ImageView(image);
                previewView.setFitWidth(88);
                previewView.setFitHeight(88);
                previewView.setPreserveRatio(true);
                previewView.setSmooth(true);
                StackPane previewWrap = new StackPane(previewView);
                previewWrap.getStyleClass().add("composer-attachment-image-wrap");
                preview = previewWrap;
                preview.setManaged(true);
                preview.setVisible(true);
            }
        } else {
            StackPane previewWrap = new StackPane(createIcon(attachmentIconLiteral(attachment), "composer-attachment-file-icon"));
            previewWrap.getStyleClass().add("composer-attachment-file-wrap");
            preview = previewWrap;
            preview.setManaged(true);
            preview.setVisible(true);
        }

        Label name = new Label(attachment.getFileName());
        name.getStyleClass().add("composer-attachment-name");
        name.setWrapText(true);

        Label meta = new Label(attachmentSizeText(attachment.getSizeBytes()));
        meta.getStyleClass().add("composer-attachment-meta");

        Button removeButton = new Button();
        removeButton.setText(null);
        removeButton.setFocusTraversable(false);
        removeButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        removeButton.getStyleClass().add("composer-attachment-remove");
        removeButton.setGraphic(createIcon("fas-times", "composer-attachment-remove-icon"));
        removeButton.setOnAction(event -> {
            pendingComposerAttachments.removeIf(candidate -> Objects.equals(candidate.getId(), attachment.getId()));
            refreshComposerAttachmentsUi();
            refreshComposerState();
        });

        HBox header = new HBox(name, new Region(), removeButton);
        header.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);

        card.getChildren().add(header);
        if (preview.isManaged()) {
            card.getChildren().add(preview);
        }
        card.getChildren().add(meta);
        return card;
    }

    private String probeAttachmentContentType(Path file) throws IOException {
        String detected = Files.probeContentType(file);
        if (detected != null && !detected.isBlank()) {
            return detected;
        }

        return switch (attachmentExtension(file == null || file.getFileName() == null ? null : file.getFileName().toString())) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            case "txt", "log", "md", "json", "xml", "yaml", "yml", "csv" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private String attachmentExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isImageAttachment(MessageAttachment attachment) {
        if (attachment == null) {
            return false;
        }
        String contentType = attachment.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        return switch (attachmentExtension(attachment.getFileName())) {
            case "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg" -> true;
            default -> false;
        };
    }

    private boolean hasImageAttachment(List<MessageAttachment> attachments) {
        return attachments != null && attachments.stream().anyMatch(this::isImageAttachment);
    }

    private Image decodeAttachmentImage(MessageAttachment attachment) {
        if (attachment == null) {
            return null;
        }

        String attachmentId = attachment.getId();
        if (attachmentId != null) {
            Image cached = attachmentImageCache.get(attachmentId);
            if (cached != null) {
                return cached;
            }
        }

        Image image = decodeBase64Image(attachment.getBase64Content());
        if (image != null && attachmentId != null) {
            attachmentImageCache.put(attachmentId, image);
        }
        return image;
    }

    private String attachmentSizeText(long sizeBytes) {
        if (sizeBytes < 1_000) {
            return sizeBytes + " B";
        }
        if (sizeBytes < 1_000_000) {
            return String.format(Locale.ROOT, "%.1f KB", sizeBytes / 1_000.0);
        }
        if (sizeBytes < 1_000_000_000) {
            return String.format(Locale.ROOT, "%.1f MB", sizeBytes / 1_000_000.0);
        }
        return String.format(Locale.ROOT, "%.1f GB", sizeBytes / 1_000_000_000.0);
    }

    private String attachmentIconLiteral(MessageAttachment attachment) {
        if (attachment == null) {
            return "fas-file";
        }
        if (isImageAttachment(attachment)) {
            return "fas-image";
        }
        String extension = attachmentExtension(attachment.getFileName());
        return switch (extension) {
            case "pdf" -> "fas-file-pdf";
            case "zip", "rar", "7z", "tar", "gz" -> "fas-file-archive";
            case "txt", "log", "md", "json", "xml", "yaml", "yml", "csv" -> "fas-file-alt";
            default -> "fas-file";
        };
    }

    private Node createMessageAttachmentsBox(List<MessageAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }

        FlowPane box = new FlowPane(10, 10);
        box.getStyleClass().add("message-attachments");
        box.prefWrapLengthProperty().bind(Bindings.max(260.0, messageListView.widthProperty().subtract(180)));
        for (MessageAttachment attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            box.getChildren().add(isImageAttachment(attachment)
                ? createMessageImageAttachmentCard(attachment)
                : createMessageFileAttachmentCard(attachment));
        }
        return box.getChildren().isEmpty() ? null : box;
    }

    private Node createMessageImageAttachmentCard(MessageAttachment attachment) {
        Image image = decodeAttachmentImage(attachment);
        if (image == null) {
            return createMessageFileAttachmentCard(attachment);
        }

        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(320);
        imageView.setFitHeight(320);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        StackPane surface = new StackPane(imageView);
        surface.getStyleClass().add("message-image-attachment-card");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(surface.widthProperty());
        clip.heightProperty().bind(surface.heightProperty());
        clip.setArcWidth(18);
        clip.setArcHeight(18);
        surface.setClip(clip);

        Button previewButton = new Button();
        previewButton.setText(null);
        previewButton.setFocusTraversable(false);
        previewButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        previewButton.getStyleClass().add("message-image-attachment-button");
        previewButton.setGraphic(surface);
        applyTooltip(previewButton, attachment.getFileName() == null || attachment.getFileName().isBlank()
            ? "Open image"
            : attachment.getFileName());
        previewButton.setOnAction(event -> showImageAttachmentPreviewModal(attachment));

        Label name = new Label(
            attachment.getFileName() == null || attachment.getFileName().isBlank() ? "Image" : attachment.getFileName()
        );
        name.getStyleClass().add("message-image-attachment-name");

        Label meta = new Label(attachmentSizeText(attachment.getSizeBytes()));
        meta.getStyleClass().add("message-image-attachment-meta");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox footer = new HBox(8, name, spacer, meta);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("message-image-attachment-footer");

        VBox card = new VBox(8, previewButton, footer);
        card.getStyleClass().add("message-image-attachment-shell");
        return card;
    }

    private Node createMessageFileAttachmentCard(MessageAttachment attachment) {
        Node icon = createIcon(attachmentIconLiteral(attachment), "message-file-attachment-icon");
        StackPane iconWrap = new StackPane(icon);
        iconWrap.getStyleClass().add("message-file-attachment-icon-wrap");

        Label name = new Label(
            attachment.getFileName() == null || attachment.getFileName().isBlank() ? "Attachment" : attachment.getFileName()
        );
        name.getStyleClass().add("message-file-attachment-name");
        name.setWrapText(true);

        Label meta = new Label(attachmentSizeText(attachment.getSizeBytes()));
        meta.getStyleClass().add("message-file-attachment-meta");

        VBox metaBox = new VBox(4, name, meta);
        metaBox.getStyleClass().add("message-file-attachment-meta-box");
        HBox.setHgrow(metaBox, Priority.ALWAYS);

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("message-file-attachment-save");
        saveButton.setGraphic(createIcon("fas-download", "message-file-attachment-save-icon"));
        saveButton.setFocusTraversable(false);
        saveButton.setOnAction(event -> saveAttachmentAs(attachment));

        HBox card = new HBox(12, iconWrap, metaBox, saveButton);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("message-file-attachment");
        return card;
    }

    private void saveAttachmentAs(MessageAttachment attachment) {
        if (attachment == null || attachment.getBase64Content() == null || attachment.getBase64Content().isBlank()) {
            return;
        }
        if (pageContainer == null || pageContainer.getScene() == null) {
            return;
        }

        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Attachment");
            String fileName = attachment.getFileName();
            if (fileName != null && !fileName.isBlank()) {
                chooser.setInitialFileName(fileName);
            }
            java.io.File target = chooser.showSaveDialog(pageContainer.getScene().getWindow());
            if (target == null) {
                return;
            }

            byte[] bytes = Base64.getDecoder().decode(attachment.getBase64Content());
            Files.write(target.toPath(), bytes);
        } catch (IllegalArgumentException | IOException ex) {
            showError(rootMessage(ex));
        }
    }

    private void showImageAttachmentPreviewModal(MessageAttachment attachment) {
        if (attachment == null || pageContainer == null) {
            return;
        }

        Image image = decodeAttachmentImage(attachment);
        if (image == null) {
            saveAttachmentAs(attachment);
            return;
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");
        overlay.setOnMouseClicked(event -> pageContainer.getChildren().remove(overlay));

        Label titleLabel = new Label(
            attachment.getFileName() == null || attachment.getFileName().isBlank() ? "Image Attachment" : attachment.getFileName()
        );
        titleLabel.getStyleClass().add("modal-title");

        String subtitleText = attachmentSizeText(attachment.getSizeBytes());
        if (attachment.getContentType() != null && !attachment.getContentType().isBlank()) {
            subtitleText = subtitleText + " • " + attachment.getContentType();
        }
        Label subtitleLabel = new Label(subtitleText);
        subtitleLabel.getStyleClass().addAll("modal-subtitle", "attachment-preview-subtitle");

        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(700);
        imageView.setFitHeight(560);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        StackPane previewWrap = new StackPane(imageView);
        previewWrap.getStyleClass().add("attachment-preview-image-wrap");

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().addAll("modal-button", "modal-button-cancel");
        closeButton.setOnAction(event -> pageContainer.getChildren().remove(overlay));

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().addAll("modal-button", "modal-button-primary");
        saveButton.setOnAction(event -> saveAttachmentAs(attachment));

        HBox actions = new HBox(10, closeButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("modal-actions");

        VBox card = new VBox(12, titleLabel, subtitleLabel, previewWrap, actions);
        card.getStyleClass().addAll("modal-card", "attachment-preview-card");
        card.setMaxWidth(760);
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().add(card);
        pageContainer.getChildren().add(overlay);
        Platform.runLater(closeButton::requestFocus);
    }

    private String replyPreviewText(String content) {
        return replyPreviewText(content, 0, false);
    }

    private String replyPreviewText(String content, int attachmentCount, boolean imageAttachment) {
        if (content == null || content.isBlank()) {
            if (attachmentCount > 0) {
                if (attachmentCount == 1) {
                    return imageAttachment ? "Photo" : "Attachment";
                }
                return attachmentCount + " attachments";
            }
            return "Empty message";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 90) {
            return normalized;
        }
        return normalized.substring(0, 87) + "...";
    }

    private ReplyTarget createReplyTarget(ChatMessage message) {
        if (message == null) {
            return null;
        }
        String conversationKey = currentConversationKey();
        if (conversationKey == null) {
            return null;
        }
        return new ReplyTarget(
            conversationKey,
            message.getId(),
            message.getSenderUsername(),
            replyPreviewText(message.getContent(), message.getAttachments().size(), hasImageAttachment(message.getAttachments()))
        );
    }

    private ReplyTarget createReplyTarget(DirectMessage message) {
        if (message == null) {
            return null;
        }
        String conversationKey = currentConversationKey();
        if (conversationKey == null) {
            return null;
        }
        return new ReplyTarget(
            conversationKey,
            message.getId(),
            message.getSenderUsername(),
            replyPreviewText(message.getContent(), message.getAttachments().size(), hasImageAttachment(message.getAttachments()))
        );
    }

    private Label createUnreadBadge(int unread) {
        Label badge = new Label(Integer.toString(unread));
        badge.getStyleClass().add("unread-badge");
        return badge;
    }

    private Node createMemberSectionRow(MemberSectionHeader header) {
        Region leftLine = new Region();
        leftLine.getStyleClass().add("member-section-line");
        HBox.setHgrow(leftLine, Priority.ALWAYS);

        Label label = new Label(header.label() + " (" + header.count() + ")");
        label.getStyleClass().add("member-section-label");

        Region rightLine = new Region();
        rightLine.getStyleClass().add("member-section-line");
        HBox.setHgrow(rightLine, Priority.ALWAYS);

        HBox row = new HBox(10, leftLine, label, rightLine);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("member-section-row");
        return row;
    }

    private Node createChannelMessageRow(ChatMessage message) {
        UserSummary sender = resolveMessageSender(message.getSenderUserId());
        if (isEditingMessage(message.getId())) {
            return createMessageEditRow(
                sender,
                message.getId(),
                message.getSenderUsername(),
                TIME_FORMATTER.format(message.getCreatedAt()),
                message.getContent(),
                message.getAttachments(),
                message.getReplyTo(),
                message.getReactions(),
                () -> saveChannelMessageEdit(message),
                this::cancelMessageEdit,
                emoji -> toggleReactionForChannel(message, emoji)
            );
        }
        return createMessageRow(
            sender,
            message.getId(),
            message.getSenderUsername(),
            TIME_FORMATTER.format(message.getCreatedAt()),
            message.getContent(),
            message.getAttachments(),
            message.getEditedAt() != null,
            message.getReplyTo(),
            message.getReactions(),
            () -> setComposerReply(createReplyTarget(message)),
            emoji -> toggleReactionForChannel(message, emoji),
            isOwnMessage(message.getSenderUserId()) ? () -> startEditingChannelMessage(message) : null,
            isOwnMessage(message.getSenderUserId()) ? () -> deleteChannelMessage(message) : null
        );
    }

    private Node createDirectMessageRow(DirectMessage message) {
        UserSummary sender = resolveMessageSender(message.getSenderUserId());
        if (isEditingMessage(message.getId())) {
            return createMessageEditRow(
                sender,
                message.getId(),
                message.getSenderUsername(),
                TIME_FORMATTER.format(message.getCreatedAt()),
                message.getContent(),
                message.getAttachments(),
                message.getReplyTo(),
                message.getReactions(),
                () -> saveDmMessageEdit(message),
                this::cancelMessageEdit,
                emoji -> toggleReactionForDm(message, emoji)
            );
        }
        return createMessageRow(
            sender,
            message.getId(),
            message.getSenderUsername(),
            TIME_FORMATTER.format(message.getCreatedAt()),
            message.getContent(),
            message.getAttachments(),
            message.getEditedAt() != null,
            message.getReplyTo(),
            message.getReactions(),
            () -> setComposerReply(createReplyTarget(message)),
            emoji -> toggleReactionForDm(message, emoji),
            isOwnMessage(message.getSenderUserId()) ? () -> startEditingDmMessage(message) : null,
            isOwnMessage(message.getSenderUserId()) ? () -> deleteDmMessage(message) : null
        );
    }

    private Node createMessageRow(
        UserSummary sender,
        String messageId,
        String username,
        String time,
        String content,
        List<MessageAttachment> attachments,
        boolean edited,
        MessageReply replyTo,
        List<MessageReaction> reactions,
        Runnable onReply,
        Consumer<String> onReactionToggle,
        Runnable onEdit,
        Runnable onDelete
    ) {
        String authorName = sender == null ? username : displayName(sender);
        Node avatar = sender == null
            ? createAvatarGraphic(authorName, null, "message-avatar", 36)
            : createAvatarGraphic(sender, "message-avatar", 36);

        Label author = new Label(authorName);
        author.getStyleClass().add("message-author");

        Label timestamp = new Label(time);
        timestamp.getStyleClass().add("message-time");

        Button reactionTrigger = createReactionTriggerButton(onReactionToggle);
        Button replyTrigger = createReplyTriggerButton(onReply);
        HBox header = new HBox(8, author, timestamp);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox hoverActions = new HBox(6);
        hoverActions.setAlignment(Pos.CENTER_RIGHT);
        hoverActions.getStyleClass().add("message-hover-actions");
        hoverActions.setVisible(false);
        hoverActions.setManaged(false);
        hoverActions.setMaxWidth(Region.USE_PREF_SIZE);
        hoverActions.setMaxHeight(Region.USE_PREF_SIZE);
        hoverActions.setPickOnBounds(false);
        if (onReply != null) {
            hoverActions.getChildren().add(replyTrigger);
        }
        hoverActions.getChildren().add(reactionTrigger);
        boolean showBody = edited || (content != null && !content.isBlank());
        TextFlow body = showBody ? createMessageBody(content, edited) : null;
        Node attachmentsBox = createMessageAttachmentsBox(attachments);

        VBox meta = new VBox(2);
        if (replyTo != null) {
            meta.getChildren().add(createMessageReplyPreview(replyTo, messageId));
        }
        meta.getChildren().add(header);
        if (body != null) {
            meta.getChildren().add(body);
        }
        if (attachmentsBox != null) {
            meta.getChildren().add(attachmentsBox);
        }
        meta.getStyleClass().add("message-meta");
        StackPane metaWrap = new StackPane(meta, hoverActions);
        metaWrap.getStyleClass().add("message-meta-wrap");
        metaWrap.setMinWidth(0);
        StackPane.setAlignment(hoverActions, Pos.CENTER_RIGHT);
        HBox.setHgrow(metaWrap, Priority.ALWAYS);
        if (body != null) {
            body.prefWidthProperty().bind(Bindings.max(0.0, messageListView.widthProperty().subtract(120)));
        }

        HBox row = new HBox(12, avatar, metaWrap);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("message-row");

        Node block = wrapMessageWithReactions(row, reactions, onReactionToggle);
        if (onEdit != null || onDelete != null) {
            hoverActions.getChildren().add(createMessageOptionsButton(onEdit, onDelete, block, hoverActions));
        }
        configureMessageActionVisibility(block, hoverActions);
        return block;
    }

    private Node createMessageEditRow(
        UserSummary sender,
        String messageId,
        String username,
        String time,
        String content,
        List<MessageAttachment> attachments,
        MessageReply replyTo,
        List<MessageReaction> reactions,
        Runnable onSave,
        Runnable onCancel,
        Consumer<String> onReactionToggle
    ) {
        String authorName = sender == null ? username : displayName(sender);
        Node avatar = sender == null
            ? createAvatarGraphic(authorName, null, "message-avatar", 36)
            : createAvatarGraphic(sender, "message-avatar", 36);

        Label author = new Label(authorName);
        author.getStyleClass().add("message-author");

        Label timestamp = new Label(time);
        timestamp.getStyleClass().add("message-time");

        HBox header = new HBox(8, author, timestamp);
        header.setAlignment(Pos.CENTER_LEFT);

        TextField editInput = new TextField(editingMessageDraft == null ? content : editingMessageDraft);
        editInput.getStyleClass().add("message-edit-input");
        editInput.textProperty().addListener((obs, oldValue, newValue) -> editingMessageDraft = newValue == null ? "" : newValue);
        editInput.prefWidthProperty().bind(Bindings.max(220.0, messageListView.widthProperty().subtract(170)));
        editInput.setOnAction(event -> {
            if (onSave != null) {
                onSave.run();
            }
        });
        editInput.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                if (onCancel != null) {
                    onCancel.run();
                }
            }
        });

        if (Objects.equals(pendingEditFocusMessageId, messageId)) {
            Platform.runLater(() -> {
                editInput.requestFocus();
                editInput.selectAll();
                pendingEditFocusMessageId = null;
            });
        }

        Label hint = new Label("escape to cancel • enter to save");
        hint.getStyleClass().add("message-edit-hint");

        HBox editActions = new HBox(hint);
        editActions.setAlignment(Pos.CENTER_LEFT);
        editActions.getStyleClass().add("message-edit-actions");

        VBox editCard = new VBox(8, editInput, editActions);
        editCard.getStyleClass().add("message-edit-card");
        Node attachmentsBox = createMessageAttachmentsBox(attachments);

        VBox meta = new VBox(4);
        if (replyTo != null) {
            meta.getChildren().add(createMessageReplyPreview(replyTo, messageId));
        }
        meta.getChildren().add(header);
        meta.getChildren().add(editCard);
        if (attachmentsBox != null) {
            meta.getChildren().add(attachmentsBox);
        }
        meta.getStyleClass().add("message-meta");

        StackPane metaWrap = new StackPane(meta);
        metaWrap.getStyleClass().add("message-meta-wrap");
        metaWrap.setMinWidth(0);
        HBox.setHgrow(metaWrap, Priority.ALWAYS);

        HBox row = new HBox(12, avatar, metaWrap);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("message-row");
        return wrapMessageWithReactions(row, reactions, onReactionToggle);
    }

    private Node wrapMessageWithReactions(Node messageRow, List<MessageReaction> reactions, Consumer<String> onReactionToggle) {
        HBox reactionsRow = createReactionBar(reactions, onReactionToggle);
        if (reactionsRow.getChildren().isEmpty()) {
            return messageRow;
        }

        VBox block = new VBox(6, messageRow, reactionsRow);
        block.getStyleClass().add("message-block");
        return block;
    }

    private Button createReplyTriggerButton(Runnable onReply) {
        Button button = new Button();
        button.setText(null);
        button.setFocusTraversable(false);
        button.setAlignment(Pos.CENTER);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.getStyleClass().addAll("message-reaction-trigger", "message-reply-trigger");
        button.setGraphic(createIcon("fas-reply", "message-reply-trigger-icon"));
        applyTooltip(button, "Reply");
        button.setOnAction(event -> {
            event.consume();
            if (onReply != null) {
                onReply.run();
            }
        });
        return button;
    }

    private Button createMessageOptionsButton(Runnable onEdit, Runnable onDelete, Node hoverTarget, Node actions) {
        Button button = new Button();
        button.setText(null);
        button.setFocusTraversable(false);
        button.setAlignment(Pos.CENTER);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.getStyleClass().addAll("message-reaction-trigger", "message-options-trigger");
        button.setGraphic(createIcon("fas-ellipsis-h", "message-options-trigger-icon"));
        applyTooltip(button, "More");

        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("message-action-menu");
        if (onEdit != null) {
            menu.getItems().add(createMessageActionMenuItem("fas-pen", "Edit Message", false, onEdit));
        }
        if (onDelete != null) {
            menu.getItems().add(createMessageActionMenuItem("fas-trash-alt", "Delete Message", true, onDelete));
        }
        menu.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            actions.getProperties().put(MESSAGE_ACTIONS_PINNED_KEY, isShowing);
            refreshMessageActionVisibility(hoverTarget, actions);
        });

        button.setOnAction(event -> {
            event.consume();
            if (menu.isShowing()) {
                menu.hide();
            } else if (!menu.getItems().isEmpty()) {
                menu.show(button, Side.BOTTOM, 0, 6);
            }
        });
        return button;
    }

    private CustomMenuItem createMessageActionMenuItem(
        String iconLiteral,
        String labelText,
        boolean danger,
        Runnable action
    ) {
        Node icon = createIcon(iconLiteral, "message-action-menu-icon");
        if (danger) {
            icon.getStyleClass().add("message-action-menu-icon-danger");
        }

        Label label = new Label(labelText);
        label.getStyleClass().add("message-action-menu-label");

        HBox row = new HBox(10, icon, label);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("message-action-menu-item");
        if (danger) {
            row.getStyleClass().add("message-action-menu-item-danger");
        }

        CustomMenuItem item = new CustomMenuItem(row, true);
        item.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        return item;
    }

    private void configureMessageActionVisibility(Node hoverTarget, Node actions) {
        if (hoverTarget == null || actions == null) {
            return;
        }
        hoverTarget.hoverProperty().addListener((obs, oldValue, hovering) -> {
            refreshMessageActionVisibility(hoverTarget, actions);
        });
        refreshMessageActionVisibility(hoverTarget, actions);
    }

    private void refreshMessageActionVisibility(Node hoverTarget, Node actions) {
        if (hoverTarget == null || actions == null) {
            return;
        }
        boolean pinned = Boolean.TRUE.equals(actions.getProperties().get(MESSAGE_ACTIONS_PINNED_KEY));
        boolean visible = pinned || hoverTarget.isHover();
        actions.setVisible(visible);
        actions.setManaged(visible);
    }

    private HBox createReactionBar(List<MessageReaction> reactions, Consumer<String> onReactionToggle) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("message-reactions");

        String currentUserId = service.currentUser().map(UserSummary::getId).orElse(null);
        for (MessageReaction reaction : reactions == null ? List.<MessageReaction>of() : reactions) {
            if (reaction == null || reaction.getEmoji() == null || reaction.getEmoji().isBlank()) {
                continue;
            }
            Button button = new Button();
            button.setFocusTraversable(false);
            button.getStyleClass().add("message-reaction-button");
            button.setGraphic(createReactionButtonGraphic(reaction.getEmoji(), reaction.getUserIds().size()));
            if (currentUserId != null && reaction.getUserIds().contains(currentUserId)) {
                button.getStyleClass().add("message-reaction-button-selected");
            }
            button.setOnAction(event -> onReactionToggle.accept(reaction.getEmoji()));
            row.getChildren().add(button);
        }
        return row;
    }

    private Button createReactionTriggerButton(Consumer<String> onReactionToggle) {
        Button button = new Button();
        button.setText(null);
        button.setFocusTraversable(false);
        button.setAlignment(Pos.CENTER);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.getStyleClass().add("message-reaction-trigger");
        button.setGraphic(createIcon("fas-plus", "message-reaction-trigger-icon"));
        button.setOnAction(event -> {
            event.consume();
            showReactionPickerModal(onReactionToggle);
        });
        return button;
    }

    private Node createReactionButtonGraphic(String emoji, int count) {
        HBox content = new HBox(6, createEmojiGraphic(emoji, 18), createReactionCountLabel(count));
        content.setAlignment(Pos.CENTER_LEFT);
        return content;
    }

    private Label createReactionCountLabel(int count) {
        Label label = new Label(Integer.toString(count));
        label.getStyleClass().add("message-reaction-count");
        return label;
    }

    private TextFlow createMessageBody(String content, boolean edited) {
        TextFlow body = new TextFlow();
        body.getStyleClass().add("message-body");
        body.setLineSpacing(1);
        appendMessageContent(body, content == null ? "" : content);
        if (edited) {
            Text editedIndicator = new Text(" (edited)");
            editedIndicator.getStyleClass().add("message-edited-label");
            body.getChildren().add(editedIndicator);
        }
        return body;
    }

    private Node createMessageReplyPreview(MessageReply replyTo, String currentMessageId) {
        Region line = new Region();
        line.getStyleClass().add("message-reply-line");

        Label author = new Label(replyTo.getSenderName() == null || replyTo.getSenderName().isBlank() ? "Unknown" : replyTo.getSenderName());
        author.getStyleClass().add("message-reply-author");

        Label snippet = new Label(
            replyPreviewText(replyTo.getContent(), replyTo.getAttachmentCount(), replyTo.isImageAttachment())
        );
        snippet.getStyleClass().add("message-reply-snippet");
        snippet.setMaxWidth(Double.MAX_VALUE);

        HBox preview = new HBox(8, line, author, snippet);
        preview.setAlignment(Pos.CENTER_LEFT);
        preview.getStyleClass().add("message-reply-preview");
        if (replyTo.getMessageId() != null && !replyTo.getMessageId().equals(currentMessageId)) {
            preview.setOnMouseClicked(event -> scrollToMessage(replyTo.getMessageId()));
        }
        return preview;
    }

    private void scrollToMessage(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        for (int index = 0; index < messageListView.getItems().size(); index++) {
            Object item = messageListView.getItems().get(index);
            if (item instanceof ChatMessage message && messageId.equals(message.getId())) {
                messageListView.scrollTo(Math.max(0, index - 1));
                return;
            }
            if (item instanceof DirectMessage message && messageId.equals(message.getId())) {
                messageListView.scrollTo(Math.max(0, index - 1));
                return;
            }
        }
    }

    private void configureMentionSuggestions() {
        mentionSuggestionPopup.setAutoFix(true);
        mentionSuggestionPopup.setAutoHide(true);
        mentionSuggestionPopup.setHideOnEscape(true);
        mentionSuggestionPopup.getStyleClass().add("mention-suggestion-popup");
        mentionSuggestionPopup.setOnHidden(event -> {
            activeMentionQuery = null;
            activeMentionSuggestions.clear();
            selectedMentionSuggestionIndex = -1;
        });
    }

    private void handleComposerKeyPressed(KeyEvent event) {
        if (!mentionSuggestionPopup.isShowing() || activeMentionSuggestions.isEmpty()) {
            return;
        }
        if (event.getCode() == KeyCode.DOWN) {
            moveMentionSelection(1);
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.UP) {
            moveMentionSelection(-1);
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
            applyMentionSuggestion(activeMentionQuery, activeMentionSuggestions.get(selectedMentionSuggestionIndex));
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.ESCAPE) {
            hideMentionSuggestions();
            event.consume();
        }
    }

    private void refreshMentionSuggestions() {
        if (!canShowMentionSuggestions()) {
            hideMentionSuggestions();
            return;
        }

        String text = messageInput.getText() == null ? "" : messageInput.getText();
        MentionQuery query = resolveActiveMentionQuery(text, messageInput.getCaretPosition());
        if (query == null) {
            hideMentionSuggestions();
            return;
        }

        List<MentionSuggestion> suggestions = mentionSuggestionsForQuery(query.queryText());
        if (suggestions.isEmpty()) {
            hideMentionSuggestions();
            return;
        }

        activeMentionQuery = query;
        activeMentionSuggestions.clear();
        activeMentionSuggestions.addAll(suggestions);
        if (selectedMentionSuggestionIndex < 0 || selectedMentionSuggestionIndex >= activeMentionSuggestions.size()) {
            selectedMentionSuggestionIndex = 0;
        }
        renderMentionSuggestions();
        showMentionSuggestionsPopup();
    }

    private boolean canShowMentionSuggestions() {
        if (messageInput == null || messageInput.getScene() == null || !messageInput.isFocused()) {
            return false;
        }
        if (!service.isConnected() || service.currentUser().isEmpty()) {
            return false;
        }
        if (activeMode == ConversationMode.CHANNEL) {
            return selectedServerId != null && selectedChannelId != null && composerBar != null && composerBar.isVisible();
        }
        if (activeMode == ConversationMode.DM) {
            if (selectedDmUserId == null || composerBar == null || !composerBar.isVisible()) {
                return false;
            }
            UserProfileDetails profile = resolveSelectedDmProfile(false).orElse(null);
            return profile == null || (!profile.isBlockedByRequester() && !profile.isBlockedRequester());
        }
        return false;
    }

    private MentionQuery resolveActiveMentionQuery(String text, int caretPosition) {
        if (text == null || caretPosition < 0 || caretPosition > text.length()) {
            return null;
        }

        int triggerIndex = caretPosition - 1;
        while (triggerIndex >= 0) {
            char current = text.charAt(triggerIndex);
            if (current == '@') {
                break;
            }
            if (Character.isWhitespace(current)) {
                return null;
            }
            triggerIndex--;
        }

        if (triggerIndex < 0 || text.charAt(triggerIndex) != '@') {
            return null;
        }
        if (triggerIndex > 0 && isMentionTokenCharacter(text.charAt(triggerIndex - 1))) {
            return null;
        }

        for (int index = triggerIndex + 1; index < caretPosition; index++) {
            if (!isMentionTokenCharacter(text.charAt(index))) {
                return null;
            }
        }

        int tokenEnd = caretPosition;
        while (tokenEnd < text.length() && isMentionTokenCharacter(text.charAt(tokenEnd))) {
            tokenEnd++;
        }

        return new MentionQuery(triggerIndex, tokenEnd, text.substring(triggerIndex + 1, caretPosition));
    }

    private boolean isMentionTokenCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '-' || value == '.';
    }

    private List<MentionSuggestion> mentionSuggestionsForQuery(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        List<MentionSuggestion> suggestions = new ArrayList<>();

        if (activeMode == ConversationMode.CHANNEL && "@everyone".startsWith("@" + query)) {
            suggestions.add(
                new MentionSuggestion(
                    MentionSuggestionKind.EVERYONE,
                    "@everyone",
                    "@everyone",
                    "Notify everyone in this channel",
                    null
                )
            );
        }

        List<UserSummary> users = mentionableUsersForActiveConversation();
        users.stream()
            .filter(user -> mentionUserMatchRank(user, query) < Integer.MAX_VALUE)
            .sorted(
                Comparator.comparingInt((UserSummary user) -> mentionUserMatchRank(user, query))
                    .thenComparingInt(this::memberPresenceSortRank)
                    .thenComparing(user -> displayName(user).toLowerCase(Locale.ROOT))
            )
            .limit(Math.max(0, 8 - suggestions.size()))
            .map(user -> new MentionSuggestion(
                MentionSuggestionKind.USER,
                "@" + user.getUsername(),
                "@" + displayName(user),
                "@" + user.getUsername() + " • " + statusLabelFor(user),
                user
            ))
            .forEach(suggestions::add);

        return suggestions;
    }

    private int mentionUserMatchRank(UserSummary user, String query) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return Integer.MAX_VALUE;
        }
        if (query == null || query.isBlank()) {
            return 10;
        }

        String username = user.getUsername().toLowerCase(Locale.ROOT);
        String name = displayName(user).toLowerCase(Locale.ROOT);
        if (username.equals(query)) {
            return 0;
        }
        if (name.equals(query)) {
            return 1;
        }
        if (username.startsWith(query)) {
            return 2;
        }
        if (name.startsWith(query)) {
            return 3;
        }
        if (username.contains(query)) {
            return 4;
        }
        if (name.contains(query)) {
            return 5;
        }
        return Integer.MAX_VALUE;
    }

    private List<UserSummary> mentionableUsersForActiveConversation() {
        Map<String, UserSummary> byId = new LinkedHashMap<>();
        if (activeMode == ConversationMode.CHANNEL) {
            for (UserSummary member : allChannelMembers) {
                if (member != null && member.getId() != null) {
                    byId.putIfAbsent(member.getId(), member);
                }
            }
        } else if (activeMode == ConversationMode.DM && selectedDmUserId != null) {
            service.knownUser(selectedDmUserId).ifPresent(user -> byId.putIfAbsent(user.getId(), user));
            UserProfileDetails profile = resolveSelectedDmProfile(false).orElse(null);
            if (profile != null && profile.getId() != null) {
                byId.putIfAbsent(
                    profile.getId(),
                    new UserSummary(
                        profile.getId(),
                        profile.getName(),
                        profile.getUsername(),
                        profile.isOnline(),
                        profile.getStatus(),
                        profile.getProfileImageBase64()
                    )
                );
            }
        }
        service.currentUser().map(UserSummary::getId).ifPresent(byId::remove);
        return new ArrayList<>(byId.values());
    }

    private void moveMentionSelection(int delta) {
        if (activeMentionSuggestions.isEmpty()) {
            return;
        }
        selectedMentionSuggestionIndex = (selectedMentionSuggestionIndex + delta + activeMentionSuggestions.size())
            % activeMentionSuggestions.size();
        renderMentionSuggestions();
    }

    private void renderMentionSuggestions() {
        MentionQuery query = activeMentionQuery;
        if (query == null) {
            hideMentionSuggestions();
            return;
        }

        double rowWidth = Math.max(280, messageInput.getWidth() - 4);
        List<CustomMenuItem> items = new ArrayList<>();
        for (int index = 0; index < activeMentionSuggestions.size(); index++) {
            MentionSuggestion suggestion = activeMentionSuggestions.get(index);
            HBox row = createMentionSuggestionRow(suggestion, index == selectedMentionSuggestionIndex, rowWidth);
            CustomMenuItem item = new CustomMenuItem(row, false);
            MentionSuggestion currentSuggestion = suggestion;
            item.setOnAction(event -> applyMentionSuggestion(query, currentSuggestion));
            row.setOnMousePressed(event -> {
                event.consume();
                applyMentionSuggestion(query, currentSuggestion);
            });
            items.add(item);
        }
        mentionSuggestionPopup.getItems().setAll(items);
    }

    private HBox createMentionSuggestionRow(MentionSuggestion suggestion, boolean selected, double width) {
        Node leadingGraphic;
        if (suggestion.kind() == MentionSuggestionKind.EVERYONE || suggestion.user() == null) {
            StackPane iconWrap = new StackPane(createIcon("fas-users", "mention-suggestion-everyone-icon"));
            iconWrap.getStyleClass().add("mention-suggestion-everyone-wrap");
            leadingGraphic = iconWrap;
        } else {
            Region presence = new Region();
            presence.getStyleClass().addAll("presence-dot", presenceStyleClass(suggestion.user()));

            StackPane avatarWrap = new StackPane(createAvatarGraphic(suggestion.user(), "mention-suggestion-avatar", 28), presence);
            avatarWrap.getStyleClass().add("mention-suggestion-avatar-wrap");
            StackPane.setAlignment(presence, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(presence, new Insets(0, -2, -2, 0));
            leadingGraphic = avatarWrap;
        }

        Label title = new Label(suggestion.label());
        title.getStyleClass().add("mention-suggestion-title");

        Label meta = new Label(suggestion.meta());
        meta.getStyleClass().add("mention-suggestion-meta");

        VBox textColumn = new VBox(2, title, meta);
        textColumn.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(10, leadingGraphic, textColumn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefWidth(width);
        row.setMinWidth(width);
        row.getStyleClass().add("mention-suggestion-row");
        if (selected) {
            row.getStyleClass().add("mention-suggestion-row-selected");
        }
        return row;
    }

    private void showMentionSuggestionsPopup() {
        if (messageInput == null || messageInput.getScene() == null || messageInput.getScene().getWindow() == null) {
            return;
        }
        if (!mentionSuggestionPopup.isShowing()) {
            mentionSuggestionPopup.show(messageInput, Side.BOTTOM, 0, 6);
        }
    }

    private void applyMentionSuggestion(MentionQuery query, MentionSuggestion suggestion) {
        if (query == null || suggestion == null) {
            hideMentionSuggestions();
            return;
        }

        String text = messageInput.getText() == null ? "" : messageInput.getText();
        int replacementEnd = Math.min(query.endIndex(), text.length());
        String insertion = suggestion.insertText();
        String suffix = "";
        if (replacementEnd >= text.length()) {
            suffix = " ";
        } else {
            char next = text.charAt(replacementEnd);
            if (!Character.isWhitespace(next) && ",.!?:;)]}".indexOf(next) < 0) {
                suffix = " ";
            }
        }

        String newText = text.substring(0, query.startIndex()) + insertion + suffix + text.substring(replacementEnd);
        int caretPosition = query.startIndex() + insertion.length() + suffix.length();
        messageInput.setText(newText);
        messageInput.positionCaret(caretPosition);
        messageInput.requestFocus();
        hideMentionSuggestions();
    }

    private void hideMentionSuggestions() {
        activeMentionQuery = null;
        activeMentionSuggestions.clear();
        selectedMentionSuggestionIndex = -1;
        mentionSuggestionPopup.hide();
    }

    private void refreshComposerPreview(String content) {
        messageInputPreview.getChildren().clear();
        String value = content == null ? "" : content;
        if (value.isBlank() || !containsRenderablePreviewToken(value)) {
            messageInputPreview.setVisible(false);
            messageInput.getStyleClass().remove("chat-input-overlaying");
            return;
        }

        appendMessageContent(messageInputPreview, value, true);
        messageInputPreview.setVisible(true);
        if (!messageInput.getStyleClass().contains("chat-input-overlaying")) {
            messageInput.getStyleClass().add("chat-input-overlaying");
        }
    }

    private void appendMessageContent(TextFlow body, String content) {
        appendMessageContent(body, content, false);
    }

    private void appendMessageContent(TextFlow body, String content, boolean composerPreview) {
        StringBuilder textBuffer = new StringBuilder();
        int index = 0;
        while (index < content.length()) {
            String emoji = matchEmoji(content, index);
            if (emoji != null) {
                flushMessageText(body, textBuffer, composerPreview);
                Node emojiNode = createEmojiGraphic(emoji, 18);
                emojiNode.getStyleClass().add("message-inline-emoji");
                body.getChildren().add(emojiNode);
                index += emoji.length();
                continue;
            }

            RenderableMention mention = matchRenderableMention(content, index);
            if (mention != null) {
                flushMessageText(body, textBuffer, composerPreview);
                body.getChildren().add(createMentionGraphic(mention, composerPreview));
                index = mention.endIndex();
                continue;
            }

            int codePoint = content.codePointAt(index);
            textBuffer.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }
        flushMessageText(body, textBuffer, composerPreview);
    }

    private void flushMessageText(TextFlow body, StringBuilder textBuffer, boolean composerPreview) {
        if (textBuffer.isEmpty()) {
            return;
        }
        Text text = new Text(textBuffer.toString());
        text.getStyleClass().add("message-body-text");
        if (composerPreview) {
            text.getStyleClass().add("chat-input-preview-text");
        }
        body.getChildren().add(text);
        textBuffer.setLength(0);
    }

    private Label createMentionGraphic(RenderableMention mention, boolean composerPreview) {
        Label label = new Label(mention.displayText());
        label.getStyleClass().add("message-mention");
        if (composerPreview) {
            label.getStyleClass().add("composer-mention");
        }
        if (mention.kind() == MentionSuggestionKind.EVERYONE) {
            label.getStyleClass().add("message-mention-everyone");
        }
        return label;
    }

    private boolean containsRenderablePreviewToken(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        int index = 0;
        while (index < content.length()) {
            String emoji = matchEmoji(content, index);
            if (emoji != null) {
                return true;
            }
            RenderableMention mention = matchRenderableMention(content, index);
            if (mention != null) {
                return true;
            }
            index += Character.charCount(content.codePointAt(index));
        }
        return false;
    }

    private RenderableMention matchRenderableMention(String content, int startIndex) {
        if (content == null || startIndex < 0 || startIndex >= content.length() || content.charAt(startIndex) != '@') {
            return null;
        }
        if (startIndex > 0 && isMentionTokenCharacter(content.charAt(startIndex - 1))) {
            return null;
        }

        int endIndex = startIndex + 1;
        while (endIndex < content.length() && isMentionTokenCharacter(content.charAt(endIndex))) {
            endIndex++;
        }
        if (endIndex <= startIndex + 1) {
            return null;
        }

        String token = content.substring(startIndex, endIndex);
        if (activeMode == ConversationMode.CHANNEL && "@everyone".equalsIgnoreCase(token)) {
            return new RenderableMention(endIndex, "@everyone", MentionSuggestionKind.EVERYONE);
        }

        String normalized = token.substring(1).toLowerCase(Locale.ROOT);
        int mentionEndIndex = endIndex;
        return mentionableUsersForActiveConversation().stream()
            .filter(user -> user != null && user.getUsername() != null)
            .filter(user -> user.getUsername().equalsIgnoreCase(normalized))
            .findFirst()
            .map(user -> new RenderableMention(mentionEndIndex, "@" + displayName(user), MentionSuggestionKind.USER))
            .orElse(null);
    }

    private String matchEmoji(String content, int startIndex) {
        if (content == null || startIndex < 0 || startIndex >= content.length()) {
            return null;
        }
        int firstCodePoint = content.codePointAt(startIndex);
        boolean keycapBase = isKeycapBase(firstCodePoint);
        if (keycapBase && !formsKeycapEmoji(content, startIndex, firstCodePoint)) {
            return null;
        }
        if (!isEmojiStartCodePoint(firstCodePoint, keycapBase)) {
            return null;
        }

        int endIndex = startIndex + Character.charCount(firstCodePoint);
        if (isRegionalIndicator(firstCodePoint)) {
            if (endIndex < content.length()) {
                int secondCodePoint = content.codePointAt(endIndex);
                if (isRegionalIndicator(secondCodePoint)) {
                    endIndex += Character.charCount(secondCodePoint);
                }
            }
            return content.substring(startIndex, endIndex);
        }

        if (keycapBase) {
            int cursor = endIndex;
            if (cursor < content.length() && content.codePointAt(cursor) == 0xFE0F) {
                cursor += Character.charCount(0xFE0F);
            }
            if (cursor < content.length() && content.codePointAt(cursor) == 0x20E3) {
                return content.substring(startIndex, cursor + Character.charCount(0x20E3));
            }
        }

        while (endIndex < content.length()) {
            int nextCodePoint = content.codePointAt(endIndex);
            if (isEmojiModifier(nextCodePoint) || isVariationSelector(nextCodePoint)) {
                endIndex += Character.charCount(nextCodePoint);
                continue;
            }
            if (nextCodePoint == 0x200D) {
                int joinerIndex = endIndex;
                int cursor = endIndex + Character.charCount(nextCodePoint);
                if (cursor >= content.length()) {
                    break;
                }
                int joinedCodePoint = content.codePointAt(cursor);
                if (!isEmojiStartCodePoint(joinedCodePoint, isKeycapBase(joinedCodePoint))) {
                    endIndex = joinerIndex;
                    break;
                }
                endIndex = cursor + Character.charCount(joinedCodePoint);
                continue;
            }
            break;
        }

        String candidate = content.substring(startIndex, endIndex);
        return emojiCodepointsFor(candidate) == null ? null : candidate;
    }

    private boolean containsRenderableEmoji(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        int index = 0;
        while (index < content.length()) {
            String emoji = matchEmoji(content, index);
            if (emoji != null) {
                return true;
            }
            index += Character.charCount(content.codePointAt(index));
        }
        return false;
    }

    private boolean formsKeycapEmoji(String content, int startIndex, int baseCodePoint) {
        if (content == null || startIndex < 0 || startIndex >= content.length() || !isKeycapBase(baseCodePoint)) {
            return false;
        }

        int cursor = startIndex + Character.charCount(baseCodePoint);
        if (cursor < content.length() && content.codePointAt(cursor) == 0xFE0F) {
            cursor += Character.charCount(0xFE0F);
        }
        return cursor < content.length() && content.codePointAt(cursor) == 0x20E3;
    }

    private boolean isEmojiStartCodePoint(int codePoint, boolean keycapBase) {
        if (codePoint < 0) {
            return false;
        }
        return Character.getType(codePoint) == Character.OTHER_SYMBOL
            || isRegionalIndicator(codePoint)
            || keycapBase;
    }

    private boolean isRegionalIndicator(int codePoint) {
        return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
    }

    private boolean isKeycapBase(int codePoint) {
        return codePoint == '#' || codePoint == '*' || (codePoint >= '0' && codePoint <= '9');
    }

    private boolean isEmojiModifier(int codePoint) {
        return codePoint >= 0x1F3FB && codePoint <= 0x1F3FF;
    }

    private boolean isVariationSelector(int codePoint) {
        return codePoint == 0xFE0F;
    }

    private Node createEmojiGraphic(String emoji, double size) {
        Label fallback = new Label(emoji);
        fallback.getStyleClass().add("reaction-fallback-emoji");

        Image image = reactionEmojiImage(emoji);
        if (image != null && !image.isError()) {
            return createEmojiImageView(image, size);
        }

        StackPane wrapper = new StackPane(fallback);
        ObjectProperty<Image> imageProperty = emojiImageProperty(emoji);
        Image currentImage = imageProperty.get();
        if (currentImage != null && !currentImage.isError()) {
            return createEmojiImageView(currentImage, size);
        }
        imageProperty.addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isError()) {
                Platform.runLater(() -> wrapper.getChildren().setAll(createEmojiImageView(newValue, size)));
            }
        });
        ensureEmojiImageLoaded(emoji);
        return wrapper;
    }

    private ImageView createEmojiImageView(Image image, double size) {
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private Image reactionEmojiImage(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            return null;
        }
        Image cached = reactionEmojiCache.get(emoji);
        if (cached != null) {
            return cached;
        }
        Image bundled = bundledReactionEmojiImage(emoji);
        if (bundled != null && !bundled.isError()) {
            reactionEmojiCache.putIfAbsent(emoji, bundled);
            return bundled;
        }
        Image local = cachedReactionEmojiImage(emoji);
        if (local != null && !local.isError()) {
            reactionEmojiCache.putIfAbsent(emoji, local);
            return local;
        }
        return null;
    }

    private Image bundledReactionEmojiImage(String emoji) {
        String resourcePath = bundledReactionEmojiResourcePath(emoji);
        if (resourcePath == null) {
            return null;
        }
        var url = HelloApplication.class.getResource(resourcePath);
        if (url == null) {
            return null;
        }
        return new Image(url.toExternalForm());
    }

    private ObjectProperty<Image> emojiImageProperty(String emoji) {
        return reactionEmojiProperties.computeIfAbsent(
            emoji,
            key -> new SimpleObjectProperty<>(reactionEmojiImage(key))
        );
    }

    private void ensureEmojiImageLoaded(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            return;
        }
        ObjectProperty<Image> imageProperty = emojiImageProperty(emoji);
        if (imageProperty.get() != null || !pendingEmojiDownloads.add(emoji)) {
            return;
        }
        CompletableFuture
            .supplyAsync(() -> downloadEmojiImage(emoji), emojiImageLoader)
            .whenComplete((image, error) -> {
                pendingEmojiDownloads.remove(emoji);
                if (error != null || image == null || image.isError()) {
                    return;
                }
                reactionEmojiCache.put(emoji, image);
                Platform.runLater(() -> imageProperty.set(image));
            });
    }

    private Image cachedReactionEmojiImage(String emoji) {
        Path cachePath = emojiCachePath(emoji);
        if (cachePath == null || !Files.isRegularFile(cachePath)) {
            return null;
        }
        return new Image(cachePath.toUri().toString(), false);
    }

    private Image downloadEmojiImage(String emoji) {
        String codepoints = emojiCodepointsFor(emoji);
        if (codepoints == null || codepoints.isBlank()) {
            return null;
        }
        String normalized = codepoints.replace("-fe0f", "");
        Path cachePath = EMOJI_CACHE_DIRECTORY.resolve(normalized + ".png");
        if (Files.isRegularFile(cachePath)) {
            return new Image(cachePath.toUri().toString(), false);
        }
        for (String baseUrl : EMOJI_IMAGE_BASE_URLS) {
            byte[] bytes = downloadEmojiBytes(baseUrl + normalized + ".png");
            if (bytes == null || bytes.length == 0) {
                continue;
            }
            try {
                Files.createDirectories(EMOJI_CACHE_DIRECTORY);
                Files.write(cachePath, bytes);
            } catch (IOException ignored) {
                // The in-memory image is still usable even if cache write fails.
            }
            return new Image(new ByteArrayInputStream(bytes));
        }
        return null;
    }

    private String emojiCodepointsFor(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            return null;
        }
        String known = EMOJI_CODEPOINTS.get(emoji);
        if (known != null && !known.isBlank()) {
            return known;
        }

        StringBuilder builder = new StringBuilder();
        emoji.codePoints().forEach(codePoint -> {
            if (builder.length() > 0) {
                builder.append('-');
            }
            builder.append(Integer.toHexString(codePoint));
        });
        return builder.isEmpty() ? null : builder.toString();
    }

    private Path emojiCachePath(String emoji) {
        String codepoints = emojiCodepointsFor(emoji);
        if (codepoints == null || codepoints.isBlank()) {
            return null;
        }
        return EMOJI_CACHE_DIRECTORY.resolve(codepoints.replace("-fe0f", "") + ".png");
    }

    private byte[] downloadEmojiBytes(String urlString) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Discord Lite");
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                return null;
            }
            try (InputStream stream = connection.getInputStream()) {
                return stream.readAllBytes();
            }
        } catch (IOException ex) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String bundledReactionEmojiResourcePath(String emoji) {
        return switch (emoji) {
            case "👍" -> "emoji/thumbs_up.png";
            case "❤️" -> "emoji/heart.png";
            case "😂" -> "emoji/joy.png";
            case "😮" -> "emoji/surprised.png";
            case "😢" -> "emoji/cry.png";
            case "🎉" -> "emoji/party.png";
            case "🔥" -> "emoji/fire.png";
            case "👏" -> "emoji/clap.png";
            default -> null;
        };
    }

    private void toggleReactionForChannel(ChatMessage message, String emoji) {
        if (message == null || emoji == null || emoji.isBlank()) {
            return;
        }
        try {
            service.toggleChannelReaction(message.getServerId(), message.getChannelId(), message.getId(), emoji);
            refreshMessages();
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
        }
    }

    private void toggleReactionForDm(DirectMessage message, String emoji) {
        if (message == null || emoji == null || emoji.isBlank() || selectedDmUserId == null) {
            return;
        }
        try {
            service.toggleDmReaction(selectedDmUserId, message.getId(), emoji);
            refreshMessages();
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
        }
    }

    private void saveChannelMessageEdit(ChatMessage message) {
        if (message == null) {
            return;
        }
        String nextContent = editingMessageDraft == null ? "" : editingMessageDraft;
        if (nextContent.isBlank() && (message.getAttachments() == null || message.getAttachments().isEmpty())) {
            showError("Message cannot be empty");
            return;
        }
        try {
            pendingMessageScrollId = message.getId();
            service.editChannelMessage(message.getServerId(), message.getChannelId(), message.getId(), nextContent);
            clearMessageEditState(false);
            refreshMessages();
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
        }
    }

    private void saveDmMessageEdit(DirectMessage message) {
        if (message == null || selectedDmUserId == null) {
            return;
        }
        String nextContent = editingMessageDraft == null ? "" : editingMessageDraft;
        if (nextContent.isBlank() && (message.getAttachments() == null || message.getAttachments().isEmpty())) {
            showError("Message cannot be empty");
            return;
        }
        try {
            pendingMessageScrollId = message.getId();
            service.editDmMessage(selectedDmUserId, message.getId(), nextContent);
            clearMessageEditState(false);
            refreshMessages();
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
        }
    }

    private void deleteChannelMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        if (!confirmAction("Delete Message", "Delete this message?", "This removes the message for everyone in the channel.")) {
            return;
        }
        try {
            pendingMessageScrollId = neighborMessageId(message.getId());
            if (isEditingMessage(message.getId())) {
                clearMessageEditState(false);
            }
            service.deleteChannelMessage(message.getServerId(), message.getChannelId(), message.getId());
            refreshMessages();
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
        }
    }

    private void deleteDmMessage(DirectMessage message) {
        if (message == null || selectedDmUserId == null) {
            return;
        }
        if (!confirmAction("Delete Message", "Delete this DM?", "This removes the message from the conversation for both users.")) {
            return;
        }
        try {
            pendingMessageScrollId = neighborMessageId(message.getId());
            if (isEditingMessage(message.getId())) {
                clearMessageEditState(false);
            }
            service.deleteDmMessage(selectedDmUserId, message.getId());
            refreshMessages();
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
        }
    }

    private Node createDateDividerRow(String labelText) {
        Region leftLine = new Region();
        leftLine.getStyleClass().add("message-date-line");
        HBox.setHgrow(leftLine, Priority.ALWAYS);

        Label label = new Label(labelText);
        label.getStyleClass().add("message-date-label");

        Region rightLine = new Region();
        rightLine.getStyleClass().add("message-date-line");
        HBox.setHgrow(rightLine, Priority.ALWAYS);

        HBox row = new HBox(12, leftLine, label, rightLine);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("message-date-row");
        return row;
    }

    private Node createDmIntroRow(UserProfileDetails profile) {
        if (profile == null) {
            return new Region();
        }

        Label presence = new Label();
        presence.getStyleClass().addAll("presence-dot", presenceStyleClass(profile));

        StackPane avatarWrap = new StackPane(createAvatarGraphic(profile, "dm-intro-avatar", 72), presence);
        avatarWrap.getStyleClass().add("dm-intro-avatar-wrap");
        StackPane.setAlignment(presence, Pos.BOTTOM_RIGHT);

        Label eyebrow = new Label("DIRECT MESSAGE");
        eyebrow.getStyleClass().add("dm-intro-eyebrow");

        Label name = new Label(displayName(profile));
        name.getStyleClass().add("dm-intro-title");

        Label handle = new Label("@" + profile.getUsername());
        handle.getStyleClass().add("dm-intro-handle");

        Label details = new Label(statusLabelFor(profile) + " • Registered " + registeredSinceLabel(profile));
        details.getStyleClass().add("dm-intro-meta");
        details.setWrapText(true);

        VBox meta = new VBox(4, eyebrow, name, handle, details);
        meta.setAlignment(Pos.CENTER_LEFT);

        if (!profile.getMutualServerNames().isEmpty()) {
            Label mutualLabel = new Label(mutualServersLabel(profile));
            mutualLabel.getStyleClass().add("dm-intro-section-title");

            HBox mutualServers = new HBox(8);
            mutualServers.getStyleClass().add("dm-intro-chip-row");
            populateMutualServerChips(mutualServers, profile.getMutualServerNames(), 3);

            meta.getChildren().addAll(mutualLabel, mutualServers);
        }

        HBox hero = new HBox(16, avatarWrap, meta);
        hero.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, hero);
        card.getStyleClass().add("dm-intro-card");
        return card;
    }

    private Node createChannelIntroRow(Channel channel, WorkspaceServer server) {
        if (channel == null) {
            return new Region();
        }

        FontIcon icon = createIcon("fas-hashtag", "channel-intro-icon");
        StackPane iconWrap = new StackPane(icon);
        iconWrap.getStyleClass().add("channel-intro-icon-wrap");

        Label eyebrow = new Label(server == null ? "CHANNEL" : "SERVER • " + server.getName());
        eyebrow.getStyleClass().add("channel-intro-eyebrow");

        Label title = new Label("#" + channel.getName());
        title.getStyleClass().add("channel-intro-title");

        List<String> details = new ArrayList<>();
        if (server != null) {
            details.add(server.getMemberUserIds().size() + " members");
        }
        if (channel.getCreatedAt() != null) {
            details.add("Created " + PROFILE_DATE_FORMATTER.format(channel.getCreatedAt()));
        }

        Label meta = new Label(details.isEmpty() ? "Channel conversation" : String.join(" • ", details));
        meta.getStyleClass().add("channel-intro-meta");
        meta.setWrapText(true);

        VBox content = new VBox(4, eyebrow, title, meta);
        content.setAlignment(Pos.CENTER_LEFT);

        HBox hero = new HBox(16, iconWrap, content);
        hero.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, hero);
        card.getStyleClass().add("channel-intro-card");
        return card;
    }

    private List<Object> buildConversationRows(
        WorkspaceServer server,
        Channel channel,
        List<ChatMessage> messages,
        int unreadCount
    ) {
        List<Object> rows = new ArrayList<>();
        if (server != null && channel != null) {
            rows.add(new ConversationChannelIntro(channel, server));
        }
        LocalDate previousDate = null;
        int unreadStartIndex = unreadCount > 0 ? Math.max(0, messages.size() - unreadCount) : -1;
        for (int index = 0; index < messages.size(); index++) {
            ChatMessage message = messages.get(index);
            LocalDate currentDate = message.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            if (!currentDate.equals(previousDate)) {
                rows.add(new ConversationDateDivider(currentDate, dateDividerLabel(currentDate)));
                previousDate = currentDate;
            }
            if (index == unreadStartIndex) {
                rows.add(new ConversationUnreadDivider("New"));
            }
            rows.add(message);
        }
        return rows;
    }

    private List<Object> buildConversationRowsForDms(List<DirectMessage> messages, UserProfileDetails profile, int unreadCount) {
        List<Object> rows = new ArrayList<>();
        if (profile != null) {
            rows.add(new ConversationProfileIntro(profile));
        }
        LocalDate previousDate = null;
        int unreadStartIndex = unreadCount > 0 ? Math.max(0, messages.size() - unreadCount) : -1;
        for (int index = 0; index < messages.size(); index++) {
            DirectMessage message = messages.get(index);
            LocalDate currentDate = message.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            if (!currentDate.equals(previousDate)) {
                rows.add(new ConversationDateDivider(currentDate, dateDividerLabel(currentDate)));
                previousDate = currentDate;
            }
            if (index == unreadStartIndex) {
                rows.add(new ConversationUnreadDivider("New"));
            }
            rows.add(message);
        }
        return rows;
    }

    private Node createUnreadDividerRow(String labelText) {
        Region leadingLine = new Region();
        leadingLine.getStyleClass().add("message-unread-line");
        HBox.setHgrow(leadingLine, Priority.ALWAYS);

        Region trailingLine = new Region();
        trailingLine.getStyleClass().add("message-unread-line");
        HBox.setHgrow(trailingLine, Priority.ALWAYS);

        Label label = new Label(labelText);
        label.getStyleClass().add("message-unread-label");

        HBox row = new HBox(10, leadingLine, label, trailingLine);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("message-unread-row");
        return row;
    }

    private String dateDividerLabel(LocalDate date) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (date.equals(today)) {
            return "Today";
        }
        if (date.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return DATE_FORMATTER.format(date);
    }

    private void animateMessageAppearance(Node row, int rowIndex, int listSize) {
        if (rowIndex < Math.max(0, listSize - 3)) {
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(180), row);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    private void animateMessageListPulse() {
        FadeTransition fade = new FadeTransition(Duration.millis(150), messageListView);
        fade.setFromValue(0.9);
        fade.setToValue(1.0);
        fade.play();
    }

    private void animateSidebarSwitch(Node pane) {
        if (pane == null) {
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(120), pane);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    private void animateServerTabState(Node wrapper, Node activePill, boolean selected, boolean hovering) {
        double targetScale = selected ? 1.08 : hovering ? 1.04 : 1.0;
        double targetTranslateY = selected ? -3 : hovering ? -1.5 : 0;
        double targetPillScaleY = selected ? 1.0 : hovering ? 0.55 : 0.2;
        double targetPillOpacity = selected ? 1.0 : hovering ? 0.5 : 0.0;

        ScaleTransition wrapperScale = new ScaleTransition(Duration.millis(180), wrapper);
        wrapperScale.setInterpolator(Interpolator.EASE_BOTH);
        wrapperScale.setToX(targetScale);
        wrapperScale.setToY(targetScale);

        TranslateTransition wrapperFloat = new TranslateTransition(Duration.millis(180), wrapper);
        wrapperFloat.setInterpolator(Interpolator.EASE_BOTH);
        wrapperFloat.setToY(targetTranslateY);

        ScaleTransition pillScale = new ScaleTransition(Duration.millis(180), activePill);
        pillScale.setInterpolator(Interpolator.EASE_BOTH);
        pillScale.setToX(1.0);
        pillScale.setToY(targetPillScaleY);

        FadeTransition pillFade = new FadeTransition(Duration.millis(160), activePill);
        pillFade.setInterpolator(Interpolator.EASE_BOTH);
        pillFade.setToValue(targetPillOpacity);

        new ParallelTransition(wrapperScale, wrapperFloat, pillScale, pillFade).play();
    }

    private String initials(String value) {
        if (value == null || value.isBlank()) {
            return "?";
        }
        String normalized = value.trim();
        String[] parts = normalized.split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return normalized.substring(0, 1).toUpperCase();
    }

    private String colorForName(String value) {
        if (value == null || value.isBlank()) {
            return "#5865f2";
        }
        String[] palette = new String[] {
            "#5865f2",
            "#3ba55d",
            "#ed4245",
            "#faa61a",
            "#57f287",
            "#eb459e",
            "#9b59b6",
            "#1abc9c",
            "#3498db",
            "#e67e22"
        };
        int idx = Math.abs(value.toLowerCase().hashCode()) % palette.length;
        return palette[idx];
    }

    private String displayName(UserSummary user) {
        if (user == null) {
            return "Unknown";
        }
        return user.displayName();
    }

    private String displayName(UserProfileDetails profile) {
        if (profile == null) {
            return "Unknown";
        }
        return profile.displayName();
    }

    private String profileSearchText(UserSummary user) {
        if (user == null) {
            return "";
        }
        return (displayName(user) + " " + (user.getUsername() == null ? "" : user.getUsername())).toLowerCase();
    }

    private int memberPresenceSortRank(UserSummary user) {
        if (user == null || !user.isOnline()) {
            return 4;
        }

        UserStatus status = user.getStatus() == null ? UserStatus.ACTIVE : user.getStatus();
        return switch (status) {
            case ACTIVE -> 0;
            case IDLE -> 1;
            case DO_NOT_DISTURB -> 2;
            case INVISIBLE -> 3;
        };
    }

    private void sortChannelMembersByPresence() {
        allChannelMembers.sort(
            Comparator.comparingInt(this::memberPresenceSortRank)
                .thenComparing(user -> displayName(user).toLowerCase(Locale.ROOT))
        );
    }

    private String statusLabel(UserStatus status) {
        UserStatus resolved = status == null ? UserStatus.ACTIVE : status;
        return switch (resolved) {
            case ACTIVE -> "Active";
            case IDLE -> "Idle";
            case DO_NOT_DISTURB -> "Do Not Disturb";
            case INVISIBLE -> "Invisible";
        };
    }

    private String statusDescription(UserStatus status) {
        UserStatus resolved = status == null ? UserStatus.ACTIVE : status;
        return switch (resolved) {
            case ACTIVE -> "Show a green dot and appear fully available.";
            case IDLE -> "Show a yellow moon when you are away for a while.";
            case DO_NOT_DISTURB -> "Show a red minus and mute interruptions.";
            case INVISIBLE -> "Appear offline to everyone else while staying connected.";
        };
    }

    private String statusIconLiteral(UserStatus status) {
        UserStatus resolved = status == null ? UserStatus.ACTIVE : status;
        return switch (resolved) {
            case ACTIVE -> "fas-circle";
            case IDLE -> "fas-moon";
            case DO_NOT_DISTURB -> "fas-minus-circle";
            case INVISIBLE -> "fas-eye-slash";
        };
    }

    private String statusIconStyleClass(UserStatus status) {
        UserStatus resolved = status == null ? UserStatus.ACTIVE : status;
        return switch (resolved) {
            case ACTIVE -> "status-icon-active";
            case IDLE -> "status-icon-idle";
            case DO_NOT_DISTURB -> "status-icon-dnd";
            case INVISIBLE -> "status-icon-invisible";
        };
    }

    private UserStatus resolveUserBarStatus(UserSummary user) {
        if (user == null || !service.isConnected()) {
            return UserStatus.INVISIBLE;
        }
        return user.getStatus() == null ? UserStatus.ACTIVE : user.getStatus();
    }

    private String userBarPresenceStyleClass(UserStatus status) {
        UserStatus resolved = status == null ? UserStatus.INVISIBLE : status;
        return switch (resolved) {
            case ACTIVE -> "user-bar-presence-active";
            case IDLE -> "user-bar-presence-idle";
            case DO_NOT_DISTURB -> "user-bar-presence-dnd";
            case INVISIBLE -> "user-bar-presence-invisible";
        };
    }

    private String userBarStatusIndicatorStyleClass(UserStatus status) {
        UserStatus resolved = status == null ? UserStatus.INVISIBLE : status;
        return switch (resolved) {
            case ACTIVE -> "user-bar-status-active";
            case IDLE -> "user-bar-status-idle";
            case DO_NOT_DISTURB -> "user-bar-status-dnd";
            case INVISIBLE -> "user-bar-status-invisible";
        };
    }

    private void refreshUserBarAvatar(UserSummary user) {
        if (userBarAvatarPane == null) {
            return;
        }

        Node avatar = user == null
            ? createAvatarLabel("Guest", "user-bar-avatar")
            : createAvatarGraphic(user, "user-bar-avatar", 40);

        if (userBarStatusIndicator == null) {
            userBarAvatarPane.getChildren().setAll(avatar);
            return;
        }

        userBarStatusIndicator.getStyleClass().removeAll(
            "user-bar-status-active",
            "user-bar-status-idle",
            "user-bar-status-dnd",
            "user-bar-status-invisible"
        );
        userBarStatusIndicator.getStyleClass().add(userBarStatusIndicatorStyleClass(resolveUserBarStatus(user)));
        userBarAvatarPane.getChildren().setAll(avatar, userBarStatusIndicator);
    }

    private void refreshPresenceButtonIcon(UserSummary user) {
        if (userBarPresenceButton == null) {
            return;
        }

        UserStatus status = resolveUserBarStatus(user);
        userBarPresenceButton.getStyleClass().removeAll(
            "user-bar-presence-active",
            "user-bar-presence-idle",
            "user-bar-presence-dnd",
            "user-bar-presence-invisible"
        );
        userBarPresenceButton.getStyleClass().add(userBarPresenceStyleClass(status));
        FontIcon icon = createIcon(statusIconLiteral(status), "user-bar-status-icon");
        icon.getStyleClass().add(statusIconStyleClass(status));
        userBarPresenceButton.setGraphic(icon);
        applyTooltip(userBarPresenceButton, "Quick status: " + statusLabel(status));
    }

    private String statusLabelFor(UserSummary user) {
        if (user == null) {
            return "Offline";
        }
        if (!user.isOnline()) {
            return user.getStatus() == UserStatus.INVISIBLE ? "Invisible" : "Offline";
        }
        return statusLabel(user.getStatus());
    }

    private String statusLabelFor(UserProfileDetails profile) {
        if (profile == null) {
            return "Offline";
        }
        if (!profile.isOnline()) {
            return profile.getStatus() == UserStatus.INVISIBLE ? "Invisible" : "Offline";
        }
        return statusLabel(profile.getStatus());
    }

    private String statusLabelForCurrentUser(UserSummary user) {
        if (!service.isConnected()) {
            return "Offline";
        }
        return statusLabel(user == null ? UserStatus.ACTIVE : user.getStatus());
    }

    private String presenceStyleClass(UserSummary user) {
        if (user == null || !user.isOnline()) {
            return "presence-offline";
        }
        UserStatus status = user.getStatus() == null ? UserStatus.ACTIVE : user.getStatus();
        return switch (status) {
            case IDLE -> "presence-idle";
            case DO_NOT_DISTURB -> "presence-dnd";
            default -> "presence-online";
        };
    }

    private String presenceStyleClass(UserProfileDetails profile) {
        if (profile == null || !profile.isOnline()) {
            return "presence-offline";
        }
        UserStatus status = profile.getStatus() == null ? UserStatus.ACTIVE : profile.getStatus();
        return switch (status) {
            case IDLE -> "presence-idle";
            case DO_NOT_DISTURB -> "presence-dnd";
            default -> "presence-online";
        };
    }

    private Label createAvatarLabel(String displayName, String styleClass) {
        Label avatar = new Label(initials(displayName));
        avatar.getStyleClass().add(styleClass);
        avatar.setStyle("-fx-background-color: " + colorForName(displayName) + ";");
        return avatar;
    }

    private Node createAvatarGraphic(UserSummary user, String styleClass, double size) {
        return createAvatarGraphic(displayName(user), user == null ? null : user.getProfileImageBase64(), styleClass, size);
    }

    private Node createAvatarGraphic(UserProfileDetails profile, String styleClass, double size) {
        return createAvatarGraphic(displayName(profile), profile == null ? null : profile.getProfileImageBase64(), styleClass, size);
    }

    private Node createAvatarGraphic(String displayName, String avatarBase64, String styleClass, double size) {
        Image image = decodeBase64Image(avatarBase64);
        if (image != null) {
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(false);
            Circle clip = new Circle(size / 2);
            clip.setCenterX(size / 2);
            clip.setCenterY(size / 2);
            imageView.setClip(clip);
            imageView.getStyleClass().add("avatar-image");
            StackPane wrapper = new StackPane(imageView);
            if (styleClass != null && !styleClass.isBlank()) {
                wrapper.getStyleClass().add(styleClass);
            }
            wrapper.setMinSize(size, size);
            wrapper.setPrefSize(size, size);
            wrapper.setMaxSize(size, size);
            wrapper.setAlignment(Pos.CENTER);
            return wrapper;
        }
        return createAvatarLabel(displayName, styleClass);
    }

    private void populateServerIconWrapper(StackPane wrapper, WorkspaceServer server, String labelStyleClass, double size, double radius) {
        if (wrapper == null) {
            return;
        }
        Node content = createServerIconGraphic(server, labelStyleClass, size, radius);
        wrapper.getChildren().setAll(content);
        if (server != null && server.getIconImageBase64() != null && !server.getIconImageBase64().isBlank()) {
            wrapper.setStyle("");
        } else {
            wrapper.setStyle("-fx-background-color: " + colorForName(server == null ? "" : server.getName()) + ";");
        }
    }

    private Node createServerIconGraphic(WorkspaceServer server, String labelStyleClass, double size, double radius) {
        String serverName = server == null ? "Server" : server.getName();
        Image image = decodeBase64Image(server == null ? null : server.getIconImageBase64());
        if (image != null) {
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(false);
            Rectangle clip = new Rectangle(size, size);
            clip.setArcWidth(radius * 2);
            clip.setArcHeight(radius * 2);
            imageView.setClip(clip);
            imageView.getStyleClass().add("server-avatar-image");
            StackPane wrapper = new StackPane(imageView);
            wrapper.setMinSize(size, size);
            wrapper.setPrefSize(size, size);
            wrapper.setMaxSize(size, size);
            return wrapper;
        }
        Label avatar = new Label(initials(serverName));
        avatar.getStyleClass().add(labelStyleClass);
        return avatar;
    }

    private Image decodeBase64Image(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(imageBase64);
            Image image = new Image(new ByteArrayInputStream(bytes));
            return image.isError() ? null : image;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String serverBannerGradient(String serverName) {
        String[][] palettes = new String[][] {
            {"#16241f", "#285146", "#6f9a7f"},
            {"#13282d", "#1e4a57", "#5f8d93"},
            {"#1a2418", "#38512d", "#7f9f68"},
            {"#172329", "#29404e", "#6d8694"},
            {"#1c2319", "#44533b", "#8aa06d"}
        };
        String key = serverName == null || serverName.isBlank() ? "server" : serverName;
        String[] palette = palettes[Math.floorMod(key.toLowerCase(Locale.ROOT).hashCode(), palettes.length)];
        return "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, " +
            palette[0] +
            " 0%, " +
            palette[1] +
            " 56%, " +
            palette[2] +
            " 100%);";
    }

    private void refreshRegisterAvatarPreview() {
        if (registerAvatarPreview == null) {
            return;
        }
        String display = registerNameField == null || registerNameField.getText() == null || registerNameField.getText().isBlank()
            ? "You"
            : registerNameField.getText().trim();
        registerAvatarPreview.getChildren().setAll(
            createAvatarGraphic(display, registerSelectedAvatarBase64, "register-avatar-large", 72)
        );
    }

    private UserSummary resolveMessageSender(String senderUserId) {
        if (senderUserId == null || senderUserId.isBlank()) {
            return null;
        }
        Optional<UserSummary> currentUser = service.currentUser()
            .filter(user -> senderUserId.equals(user.getId()));
        if (currentUser.isPresent()) {
            return currentUser.orElseThrow();
        }
        Optional<UserSummary> knownUser = service.knownUser(senderUserId);
        if (knownUser.isPresent()) {
            return knownUser.orElseThrow();
        }
        return allChannelMembers.stream()
            .filter(member -> senderUserId.equals(member.getId()))
            .findFirst()
            .orElse(null);
    }

    private String registeredSinceLabel(UserProfileDetails profile) {
        if (profile == null || profile.getCreatedAt() == null) {
            return "unknown";
        }
        return PROFILE_DATE_FORMATTER.format(profile.getCreatedAt());
    }

    private String mutualServersLabel(UserProfileDetails profile) {
        int count = profile == null || profile.getMutualServerNames() == null ? 0 : profile.getMutualServerNames().size();
        if (count == 1) {
            return "1 mutual server";
        }
        return count + " mutual servers";
    }

    private void populateMutualServerChips(HBox row, List<String> serverNames, int maxVisible) {
        row.getChildren().clear();
        if (serverNames == null || serverNames.isEmpty()) {
            return;
        }

        int limit = Math.min(serverNames.size(), Math.max(1, maxVisible));
        for (int i = 0; i < limit; i++) {
            Label chip = new Label(serverNames.get(i));
            chip.getStyleClass().add("profile-server-chip");
            row.getChildren().add(chip);
        }
        if (serverNames.size() > limit) {
            Label moreChip = new Label("+" + (serverNames.size() - limit) + " more");
            moreChip.getStyleClass().addAll("profile-server-chip", "profile-server-chip-muted");
            row.getChildren().add(moreChip);
        }
    }

    private StringConverter<UserStatus> statusConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(UserStatus status) {
                return status == null ? "" : statusLabel(status);
            }

            @Override
            public UserStatus fromString(String string) {
                return null;
            }
        };
    }

    private void populateSuggestedUsername(String base, Label errorLabel) {
        try {
            List<String> suggestions = service.suggestUsernames(base);
            if (!suggestions.isEmpty()) {
                registerUsernameField.setText(suggestions.get(0));
                registerUsernameHintLabel.setText("Suggestions: " + String.join(", ", suggestions));
                setAuthError(errorLabel, "Username already exists. Suggested: " + suggestions.get(0));
            } else {
                setAuthError(errorLabel, "Username already exists.");
            }
        } catch (RuntimeException ex) {
            setAuthError(errorLabel, "Username already exists.");
        }
    }

    private void suggestUsernameFromRegister() {
        clearAuthErrors();
        String host = registerHostField.getText() == null ? "" : registerHostField.getText().trim();
        String portText = registerPortField.getText() == null ? "" : registerPortField.getText().trim();
        String base = registerUsernameField.getText() == null || registerUsernameField.getText().isBlank()
            ? registerNameField.getText()
            : registerUsernameField.getText();

        if (!ensureConnectedFromPage(host, portText, registerErrorLabel)) {
            return;
        }

        try {
            List<String> suggestions = service.suggestUsernames(base);
            if (suggestions.isEmpty()) {
                registerUsernameHintLabel.setText("No username suggestions available.");
                return;
            }
            registerUsernameField.setText(suggestions.get(0));
            registerUsernameHintLabel.setText("Suggestions: " + String.join(", ", suggestions));
        } catch (RuntimeException ex) {
            setAuthError(registerErrorLabel, rootMessage(ex));
        }
    }

    private void configureSelectionListeners() {
        serverListView.getSelectionModel().selectedItemProperty().addListener((obs, oldServer, newServer) -> {
            if (suppressSelectionEvents) {
                return;
            }
            if (newServer == null) {
                if (!dmHomeSelected) {
                    clearMessageEditState(false);
                    selectedServerId = null;
                    selectedChannelId = null;
                    loadedChannelGroups.clear();
                    channelGroupById.clear();
                    if (activeMode == ConversationMode.CHANNEL) {
                        activeMode = ConversationMode.NONE;
                    }
                    service.clearActiveConversation();
                }
                updateContextSidebarMode();
                refreshHeader();
                refreshMembers();
                return;
            }

            dmHomeSelected = false;
            clearMessageEditState(false);
            selectedServerId = newServer.getId();
            selectedChannelId = null;
            clearSelectedDmProfile();
            if (activeMode == ConversationMode.DM) {
                activeMode = ConversationMode.NONE;
            }
            service.clearActiveConversation();
            withSuppressedSelections(() -> dmListView.getSelectionModel().clearSelection());
            selectedDmUserId = null;
            stopLocalTyping();
            updateDmHomeButtonState();
            updateContextSidebarMode();
            refreshChannels();
            selectFirstChannelIfNeeded();
            refreshMembers();
            refreshMessages();
            refreshHeader();
        });

        channelListView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (suppressSelectionEvents) {
                return;
            }

            if (newItem instanceof ChannelGroupHeaderRow headerRow) {
                toggleChannelGroupCollapse(headerRow.group().getId());
                withSuppressedSelections(() -> channelListView.getSelectionModel().clearSelection());
                return;
            }

            Channel newChannel = newItem instanceof Channel channel ? channel : null;
            clearMessageEditState(false);
            selectedChannelId = newChannel == null ? null : newChannel.getId();
            if (newChannel != null) {
                clearSelectedDmProfile();
                activeMode = ConversationMode.CHANNEL;
                stopLocalTyping();
                withSuppressedSelections(() -> dmListView.getSelectionModel().clearSelection());
                selectedDmUserId = null;
                service.setActiveChannel(newChannel.getServerId(), newChannel.getId());
                prepareUnreadBoundaryForChannel(newChannel.getServerId(), newChannel.getId());
                dmHomeSelected = false;
                updateDmHomeButtonState();
                updateContextSidebarMode();
            } else if (activeMode == ConversationMode.CHANNEL) {
                activeMode = ConversationMode.NONE;
                clearUnreadBoundary();
                service.clearActiveConversation();
            }
            refreshMessages();
            refreshHeader();
            refreshMembers();
        });

        dmListView.getSelectionModel().selectedItemProperty().addListener((obs, oldDm, newDm) -> {
            if (suppressSelectionEvents) {
                return;
            }
            clearMessageEditState(false);
            selectedDmUserId = newDm == null ? null : newDm.getId();
            clearSelectedDmProfile();
            if (newDm != null) {
                activeMode = ConversationMode.DM;
                stopLocalTyping();
                withSuppressedSelections(() -> channelListView.getSelectionModel().clearSelection());
                selectedChannelId = null;
                service.setActiveDm(newDm.getId());
                prepareUnreadBoundaryForDm(newDm.getId());
                if (!dmHomeSelected) {
                    dmHomeSelected = true;
                    selectedServerId = null;
                    updateDmHomeButtonState();
                    withSuppressedSelections(() -> serverListView.getSelectionModel().clearSelection());
                }
                updateContextSidebarMode();
            } else if (activeMode == ConversationMode.DM) {
                activeMode = ConversationMode.NONE;
                clearUnreadBoundary();
                service.clearActiveConversation();
            }
            refreshMessages();
            refreshHeader();
            refreshMembers();
        });
    }

    private void selectDmHome() {
        dmHomeSelected = true;
        clearMessageEditState(false);
        selectedServerId = null;
        selectedChannelId = null;
        clearSelectedDmProfile();
        clearUnreadBoundary();
        service.clearActiveConversation();
        if (activeMode == ConversationMode.CHANNEL) {
            activeMode = ConversationMode.NONE;
        }
        withSuppressedSelections(() -> serverListView.getSelectionModel().clearSelection());
        updateDmHomeButtonState();
        updateContextSidebarMode();
        stopLocalTyping();
        refreshChannels();
        refreshDms();
        refreshMembers();
        refreshMessages();
        refreshHeader();
    }

    private void updateDmHomeButtonState() {
        if (dmHomeButton == null) {
            return;
        }
        dmHomeButton.getProperties().put("selected", dmHomeSelected);
        dmHomeButton.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), dmHomeSelected);
        Object wrapper = dmHomeButton.getProperties().get("tileWrapper");
        Object activePill = dmHomeButton.getProperties().get("activePill");
        if (wrapper instanceof Node tileWrapper && activePill instanceof Node tilePill) {
            animateServerTabState(tileWrapper, tilePill, dmHomeSelected, dmHomeButton.isHover());
        }
    }

    private void updateContextSidebarMode() {
        if (
            contextHeaderLabel == null ||
                contextSettingsButton == null ||
                contextActionButton == null ||
                contextGroupActionButton == null ||
                contextInviteButton == null ||
                serverBannerPane == null ||
                channelSectionPane == null ||
                dmSectionPane == null
        ) {
            return;
        }

        boolean showDm = dmHomeSelected || selectedServerId == null;
        if (showDm != sidebarDmVisible) {
            Node entering = showDm ? dmSectionPane : channelSectionPane;
            animateSidebarSwitch(entering);
        }
        dmSectionPane.setVisible(showDm);
        dmSectionPane.setManaged(showDm);
        channelSectionPane.setVisible(!showDm);
        channelSectionPane.setManaged(!showDm);
        sidebarDmVisible = showDm;

        if (showDm) {
            contextHeaderLabel.setText("DIRECT MESSAGES");
            applyTooltip(contextActionButton, "New direct message");
            contextActionButton.setVisible(true);
            contextActionButton.setManaged(true);
            contextSettingsButton.setVisible(false);
            contextSettingsButton.setManaged(false);
            contextInviteButton.setVisible(false);
            contextInviteButton.setManaged(false);
            contextGroupActionButton.setVisible(false);
            contextGroupActionButton.setManaged(false);
            serverBannerPane.setVisible(false);
            serverBannerPane.setManaged(false);
            contextActionButton.setDisable(!service.isConnected() || service.currentUser().isEmpty());
        } else {
            Optional<WorkspaceServer> selectedServer = selectedServer();
            String serverName = selectedServer.map(WorkspaceServer::getName).orElse("CHANNELS");
            contextHeaderLabel.setText(serverName.toUpperCase());
            applyTooltip(contextActionButton, "Create channel");
            boolean hasSelectedServer = selectedServer.isPresent();
            boolean canManageServer = selectedServer.map(this::canManageServerAppearance).orElse(false);
            contextInviteButton.setVisible(hasSelectedServer);
            contextInviteButton.setManaged(hasSelectedServer);
            contextGroupActionButton.setVisible(canManageServer);
            contextGroupActionButton.setManaged(canManageServer);
            contextSettingsButton.setVisible(canManageServer);
            contextSettingsButton.setManaged(canManageServer);
            contextActionButton.setVisible(canManageServer);
            contextActionButton.setManaged(canManageServer);
            contextSettingsButton.setDisable(!service.isConnected() || service.currentUser().isEmpty() || !canManageServer);
            contextInviteButton.setDisable(!service.isConnected() || service.currentUser().isEmpty() || !hasSelectedServer);
            contextGroupActionButton.setDisable(!service.isConnected() || service.currentUser().isEmpty() || !canManageServer);
            contextActionButton.setDisable(!service.isConnected() || service.currentUser().isEmpty() || !canManageServer);
            refreshServerBanner(selectedServer.orElse(null));
        }

        updateInviteServerButtonState();
    }

    private void onClientEvent(ClientEvent event) {
        if (event.type() == ClientEventType.ERROR) {
            Object payload = event.payload();
            if (payload instanceof String message) {
                showError(message);
            }
        }

        switch (event.type()) {
            case CONNECTION_CHANGED -> {
                invalidateDmProfileCache();
                if (!service.isConnected()) {
                    clearMessageEditState(false);
                    messageInput.clear();
                    clearComposerReply();
                    clearComposerAttachments();
                    selectedServerId = null;
                    selectedChannelId = null;
                    selectedDmUserId = null;
                    dmHomeSelected = true;
                    activeMode = ConversationMode.NONE;
                    if (currentPage == AppPage.CHAT) {
                        clearAuthErrors();
                        setAuthError(loginErrorLabel, "Disconnected from server.");
                        switchPage(AppPage.LOGIN, true);
                    }
                }
                refreshUnreadStateSafely();
                stopLocalTyping();
                refreshSession();
                refreshServers();
                refreshChannels();
                if (selectedServerId != null && !dmHomeSelected) {
                    selectFirstChannelIfNeeded();
                }
                refreshDms();
                refreshMembers();
                refreshMessages();
                refreshHeader();
                updateDmHomeButtonState();
                updateContextSidebarMode();
            }
            case SESSION_CHANGED -> {
                invalidateDmProfileCache();
                clearMessageEditState(false);
                messageInput.clear();
                clearComposerReply();
                clearComposerAttachments();
                selectedServerId = null;
                selectedChannelId = null;
                selectedDmUserId = null;
                dmHomeSelected = true;
                activeMode = ConversationMode.NONE;
                if (service.currentUser().isPresent()) {
                    if (currentPage != AppPage.CHAT) {
                        switchPage(AppPage.CHAT, true);
                    }
                } else if (currentPage == AppPage.CHAT) {
                    switchPage(AppPage.LOGIN, true);
                }
                refreshUnreadStateSafely();
                stopLocalTyping();
                refreshSession();
                refreshServers();
                refreshChannels();
                if (selectedServerId != null && !dmHomeSelected) {
                    selectFirstChannelIfNeeded();
                }
                refreshDms();
                refreshMembers();
                refreshMessages();
                refreshHeader();
                updateDmHomeButtonState();
                updateContextSidebarMode();
            }
            case DATA_CHANGED -> {
                invalidateDmProfileCache();
                refreshUnreadStateSafely();
                refreshSession();
                refreshServers();
                refreshChannels();
                if (selectedServerId != null && !dmHomeSelected) {
                    selectFirstChannelIfNeeded();
                }
                refreshDms();
                refreshMembers();
                refreshMessages();
                refreshHeader();
                updateContextSidebarMode();
            }
            case CHANNEL_MESSAGE, DM_MESSAGE -> {
                refreshUnreadStateSafely();
                refreshSession();
                refreshServers();
                refreshChannels();
                if (selectedServerId != null && !dmHomeSelected) {
                    selectFirstChannelIfNeeded();
                }
                refreshDms();
                refreshMembers();
                refreshMessages();
                refreshHeader();
                updateContextSidebarMode();
            }
            case UNREAD_CHANGED -> {
                refreshUnreadStateSafely();
                refreshSession();
                refreshServers();
                refreshChannels();
                if (selectedServerId != null && !dmHomeSelected) {
                    selectFirstChannelIfNeeded();
                }
                refreshDms();
                updateContextSidebarMode();
            }
            case TYPING_CHANGED -> refreshHeader();
            case ERROR -> refreshSession();
        }
    }

    private void loginFromPage() {
        clearAuthErrors();
        String host = loginHostField.getText() == null ? "" : loginHostField.getText().trim();
        String portText = loginPortField.getText() == null ? "" : loginPortField.getText().trim();
        String username = loginUsernameField.getText() == null ? "" : loginUsernameField.getText().trim();
        String password = loginPasswordField.getText();

        if (username.isBlank() || password == null || password.isBlank()) {
            setAuthError(loginErrorLabel, "Enter both username and password.");
            return;
        }

        if (!ensureConnectedFromPage(host, portText, loginErrorLabel)) {
            return;
        }

        try {
            service.login(username, password);
            loginPasswordField.clear();
            switchPage(AppPage.CHAT, true);
        } catch (RuntimeException ex) {
            setAuthError(loginErrorLabel, rootMessage(ex));
        }
    }

    private void disconnect() {
        stopLocalTyping();
        service.disconnect();
        if (currentPage == AppPage.CHAT) {
            switchPage(AppPage.WELCOME, true);
        }
    }

    private void registerFromPage() {
        clearAuthErrors();
        String host = registerHostField.getText() == null ? "" : registerHostField.getText().trim();
        String portText = registerPortField.getText() == null ? "" : registerPortField.getText().trim();
        String name = registerNameField.getText() == null ? "" : registerNameField.getText().trim();
        String username = registerUsernameField.getText() == null ? "" : registerUsernameField.getText().trim();
        String password = registerPasswordField.getText();
        String confirm = registerConfirmPasswordField.getText();

        if (name.isBlank() || username.isBlank() || password == null || password.isBlank()) {
            setAuthError(registerErrorLabel, "Name, username, and password are required.");
            return;
        }
        if (confirm == null || !confirm.equals(password)) {
            setAuthError(registerErrorLabel, "Passwords do not match.");
            return;
        }

        if (!ensureConnectedFromPage(host, portText, registerErrorLabel)) {
            return;
        }

        try {
            service.register(name, username, password);
            if (registerSelectedAvatarBase64 != null && !registerSelectedAvatarBase64.isBlank()) {
                try {
                    UserStatus status = service.currentUser().map(UserSummary::getStatus).orElse(UserStatus.ACTIVE);
                    service.updateProfile(name, status, "", "", registerSelectedAvatarBase64);
                } catch (RuntimeException ex) {
                    setAuthError(registerErrorLabel, rootMessage(ex));
                }
            }
            registerPasswordField.clear();
            registerConfirmPasswordField.clear();
            registerSelectedAvatarBase64 = null;
            refreshRegisterAvatarPreview();
            switchPage(AppPage.CHAT, true);
        } catch (RuntimeException ex) {
            String message = rootMessage(ex);
            if (message != null && message.toLowerCase().contains("username already exists")) {
                populateSuggestedUsername(username.isBlank() ? name : username, registerErrorLabel);
                return;
            }
            setAuthError(registerErrorLabel, message);
        }
    }

    private void logout() {
        stopLocalTyping();
        service.logout();
        clearAuthErrors();
        switchPage(AppPage.WELCOME, true);
    }

    private boolean ensureConnectedFromPage(String host, String portText, Label errorLabel) {
        if (host == null || host.isBlank()) {
            setAuthError(errorLabel, "Host is required.");
            return false;
        }

        final int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            setAuthError(errorLabel, "Port must be a number.");
            return false;
        }

        if (port <= 0 || port > 65535) {
            setAuthError(errorLabel, "Port must be between 1 and 65535.");
            return false;
        }

        if (service.isConnected() && (!host.equals(activeHost) || port != activePort)) {
            service.disconnect();
        }

        if (!service.isConnected()) {
            try {
                service.connect(host, port);
            } catch (RuntimeException ex) {
                setAuthError(errorLabel, rootMessage(ex));
                return false;
            }
        }

        activeHost = host;
        activePort = port;
        copyLoginConnectionToRegister();
        copyRegisterConnectionToLogin();
        return true;
    }

    private void clearAuthErrors() {
        if (loginErrorLabel != null) {
            loginErrorLabel.setText("");
            loginErrorLabel.setVisible(false);
            loginErrorLabel.setManaged(false);
        }
        if (registerErrorLabel != null) {
            registerErrorLabel.setText("");
            registerErrorLabel.setVisible(false);
            registerErrorLabel.setManaged(false);
        }
    }

    private void setAuthError(Label label, String message) {
        if (label == null) {
            return;
        }
        label.setText(message == null ? "Something went wrong." : message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void copyLoginConnectionToRegister() {
        if (registerHostField != null && loginHostField != null) {
            registerHostField.setText(loginHostField.getText());
        }
        if (registerPortField != null && loginPortField != null) {
            registerPortField.setText(loginPortField.getText());
        }
    }

    private void copyRegisterConnectionToLogin() {
        if (loginHostField != null && registerHostField != null) {
            loginHostField.setText(registerHostField.getText());
        }
        if (loginPortField != null && registerPortField != null) {
            loginPortField.setText(registerPortField.getText());
        }
    }

    private void createServer() {
        showTextInputModal(
            "Create Server",
            "Start a new server for your community.",
            "Server Name",
                "Create",
                serverName -> {
                    try {
                        clearMessageEditState(false);
                        WorkspaceServer server = service.createServer(serverName);
                        selectedServerId = server.getId();
                    dmHomeSelected = false;
                    selectedDmUserId = null;
                    activeMode = ConversationMode.NONE;
                    refreshServers();
                    refreshChannels();
                    selectFirstChannelIfNeeded();
                    refreshDms();
                    refreshMembers();
                    refreshMessages();
                    refreshHeader();
                    showInfo("Server created. Share this invite code:\n" + inviteCodeFor(server));
                } catch (RuntimeException ex) {
                    showError(rootMessage(ex));
                }
            }
        );
    }

    private void joinServer() {
        showTextInputModal(
            "Join Server",
            "Paste an invite code or server ID to join.",
            "Invite Code or Server ID",
                "Join",
                inviteCodeOrServerId -> {
                    try {
                        clearMessageEditState(false);
                        service.joinServer(inviteCodeOrServerId);
                        dmHomeSelected = false;
                    selectedDmUserId = null;
                    refreshServers();
                    selectServerFromJoinInput(inviteCodeOrServerId);
                    refreshChannels();
                    selectFirstChannelIfNeeded();
                    refreshDms();
                    refreshMembers();
                    refreshMessages();
                    refreshHeader();
                } catch (RuntimeException ex) {
                    showError(rootMessage(ex));
                }
            }
        );
    }

    private void showInviteCodeForSelectedServer() {
        showServerInviteModal();
    }

    private void showServerInviteModal() {
        if (pageContainer == null) {
            return;
        }

        Optional<WorkspaceServer> server = selectedServer();
        if (server.isEmpty()) {
            showError("Select a server first");
            return;
        }

        List<UserSummary> inviteTargets;
        try {
            inviteTargets = service.listUsers().stream()
                .filter(user -> !server.get().isMember(user.getId()))
                .filter(user -> service.currentUser().map(current -> !current.getId().equals(user.getId())).orElse(true))
                .toList();
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
            return;
        }

        String inviteCode = inviteCodeFor(server.get());

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");

        Label titleLabel = new Label("Invite People");
        titleLabel.getStyleClass().add("modal-title");
        Label subtitleLabel = new Label("Copy the invite ID or search all users by name to send a direct invite.");
        subtitleLabel.getStyleClass().add("modal-subtitle");

        Label codeLabel = new Label("INVITE ID");
        codeLabel.getStyleClass().add("modal-field-label");
        TextField inviteField = new TextField(inviteCode);
        inviteField.getStyleClass().addAll("modal-input", "modal-readonly");
        inviteField.setEditable(false);
        inviteField.setFocusTraversable(false);

        Button copyButton = new Button("Copy Invite ID");
        copyButton.getStyleClass().addAll("modal-button", "modal-button-primary");

        HBox inviteCodeRow = new HBox(10, inviteField, copyButton);
        inviteCodeRow.setAlignment(Pos.CENTER_LEFT);
        inviteCodeRow.getStyleClass().add("invite-code-row");
        HBox.setHgrow(inviteField, Priority.ALWAYS);

        Label dmLabel = new Label("ALL USERS");
        dmLabel.getStyleClass().add("modal-field-label");

        TextField searchField = new TextField();
        searchField.getStyleClass().add("modal-input");
        searchField.setPromptText("Search users by name");

        List<UserSummary> allTargets = inviteTargets;
        ObservableList<UserSummary> filteredTargets = FXCollections.observableArrayList(allTargets);
        ListView<UserSummary> listView = new ListView<>(filteredTargets);
        listView.getStyleClass().addAll("modal-list", "invite-target-list");
        listView.setPrefHeight(260);

        Label feedbackLabel = new Label();
        feedbackLabel.getStyleClass().add("invite-modal-feedback");
        feedbackLabel.setManaged(false);
        feedbackLabel.setVisible(false);

        Label emptyLabel = new Label("No users available to invite.");
        emptyLabel.getStyleClass().add("reaction-picker-empty");
        listView.setPlaceholder(emptyLabel);
        listView.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(UserSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Node avatar = createAvatarGraphic(item, "member-avatar", 36);
                Label name = new Label(displayName(item));
                name.getStyleClass().add("member-name");
                Label username = new Label("@" + item.getUsername());
                username.getStyleClass().add("invite-target-handle");
                VBox meta = new VBox(2, name, username);
                HBox.setHgrow(meta, Priority.ALWAYS);

                Button inviteButton = new Button("Invite");
                inviteButton.getStyleClass().add("invite-target-button");
                inviteButton.setOnAction(event -> {
                    event.consume();
                    inviteDmPeerToServer(server.get(), item, feedbackLabel);
                });

                HBox row = new HBox(10, avatar, meta, inviteButton);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("invite-target-row");
                row.setOnMouseClicked(event -> inviteDmPeerToServer(server.get(), item, feedbackLabel));

                setText(null);
                setGraphic(row);
            }
        });

        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            if (query.isBlank()) {
                filteredTargets.setAll(allTargets);
            } else {
                filteredTargets.setAll(
                    allTargets.stream()
                        .filter(user -> profileSearchText(user).contains(query))
                        .toList()
                );
            }
        });

        copyButton.setOnAction(event -> {
            copyTextToClipboard(inviteCode);
            setInviteModalFeedback(feedbackLabel, "Invite ID copied.", false);
        });

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().addAll("modal-button", "modal-button-cancel");
        closeButton.setOnAction(event -> pageContainer.getChildren().remove(overlay));

        HBox actions = new HBox(10, closeButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("modal-actions");

        VBox card = new VBox(
            12,
            titleLabel,
            subtitleLabel,
            codeLabel,
            inviteCodeRow,
            dmLabel,
            searchField,
            listView,
            feedbackLabel,
            actions
        );
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(560);
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().add(card);
        pageContainer.getChildren().add(overlay);
        Platform.runLater(searchField::requestFocus);
    }

    private void selectServerFromJoinInput(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim();
        serverListView.getItems().stream()
            .filter(server ->
                normalized.equals(server.getId()) ||
                    normalized.equalsIgnoreCase(inviteCodeFor(server))
            )
            .findFirst()
            .ifPresent(server -> {
                selectedServerId = server.getId();
                withSuppressedSelections(() -> serverListView.getSelectionModel().select(server));
            });
    }

    private Optional<WorkspaceServer> selectedServer() {
        if (selectedServerId == null) {
            return Optional.empty();
        }
        return serverListView.getItems().stream()
            .filter(server -> server.getId().equals(selectedServerId))
            .findFirst();
    }

    private boolean canManageServerAppearance(WorkspaceServer server) {
        if (server == null) {
            return false;
        }
        String currentUserId = currentUserId();
        if (currentUserId == null) {
            return false;
        }
        Role role = server.roleOf(currentUserId);
        return role == Role.OWNER || role == Role.ADMIN;
    }

    private String currentUserId() {
        return service.currentUser().map(UserSummary::getId).orElse(null);
    }

    private boolean isCurrentUserServerOwner(WorkspaceServer server) {
        String currentUserId = currentUserId();
        return server != null && currentUserId != null && currentUserId.equals(server.getOwnerUserId());
    }

    private boolean canModerateServerMember(WorkspaceServer server, String targetUserId) {
        if (server == null || targetUserId == null || targetUserId.isBlank()) {
            return false;
        }
        String currentUserId = currentUserId();
        if (currentUserId == null || currentUserId.equals(targetUserId)) {
            return false;
        }
        Role actorRole = server.roleOf(currentUserId);
        if (!(actorRole == Role.OWNER || actorRole == Role.ADMIN)) {
            return false;
        }
        if (!server.getMemberUserIds().contains(targetUserId)) {
            return false;
        }
        Role targetRole = server.roleOf(targetUserId);
        if (targetRole == Role.OWNER) {
            return false;
        }
        return actorRole == Role.OWNER || targetRole != Role.ADMIN;
    }

    private String serverRoleLabel(WorkspaceServer server, String userId) {
        if (server == null || userId == null || userId.isBlank()) {
            return "Member";
        }
        return switch (server.roleOf(userId)) {
            case OWNER -> "Owner";
            case ADMIN -> "Admin";
            case MEMBER -> "Member";
        };
    }

    private void refreshChatViews() {
        refreshSession();
        refreshServers();
        refreshChannels();
        if (selectedServerId != null && !dmHomeSelected) {
            selectFirstChannelIfNeeded();
        }
        refreshDms();
        refreshMembers();
        refreshMessages();
        refreshHeader();
        updateContextSidebarMode();
    }

    private void refreshUnreadStateSafely() {
        if (!service.isConnected() || service.currentUser().isEmpty()) {
            return;
        }
        try {
            service.refreshUnreadState();
        } catch (RuntimeException ignored) {
        }
    }

    private void refreshServerBanner(WorkspaceServer server) {
        if (serverBannerPane == null) {
            return;
        }
        if (server == null || dmHomeSelected) {
            serverBannerPane.getChildren().clear();
            serverBannerPane.setVisible(false);
            serverBannerPane.setManaged(false);
            return;
        }

        StackPane background = new StackPane();
        background.getStyleClass().add("server-banner-surface");

        Image coverImage = decodeBase64Image(server.getCoverImageBase64());
        if (coverImage != null) {
            ImageView coverView = new ImageView(coverImage);
            coverView.setPreserveRatio(false);
            coverView.fitWidthProperty().bind(serverBannerPane.widthProperty());
            coverView.fitHeightProperty().bind(serverBannerPane.heightProperty());
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(serverBannerPane.widthProperty());
            clip.heightProperty().bind(serverBannerPane.heightProperty());
            clip.setArcWidth(22);
            clip.setArcHeight(22);
            coverView.setClip(clip);
            background.getChildren().add(coverView);
        } else {
            background.setStyle(serverBannerGradient(server.getName()));
        }

        Region overlay = new Region();
        overlay.getStyleClass().add("server-banner-overlay");

        StackPane iconWrap = new StackPane();
        iconWrap.getStyleClass().add("server-banner-avatar-wrap");
        populateServerIconWrapper(iconWrap, server, "server-banner-avatar", 56, 16);

        Label title = new Label(server.getName());
        title.getStyleClass().add("server-banner-title");

        Label subtitle = new Label("Invite ID • " + inviteCodeFor(server));
        subtitle.getStyleClass().add("server-banner-subtitle");

        VBox meta = new VBox(4, title, subtitle);
        meta.setAlignment(Pos.CENTER_LEFT);

        HBox content = new HBox(14, iconWrap, meta);
        content.setAlignment(Pos.BOTTOM_LEFT);
        content.getStyleClass().add("server-banner-content");

        serverBannerPane.getChildren().setAll(background, overlay, content);
        StackPane.setAlignment(content, Pos.BOTTOM_LEFT);
        StackPane.setMargin(content, new Insets(16));
        serverBannerPane.setVisible(true);
        serverBannerPane.setManaged(true);
    }

    private String inviteCodeFor(WorkspaceServer server) {
        if (server == null) {
            return "";
        }
        String inviteCode = server.getInviteCode();
        if (inviteCode == null || inviteCode.isBlank()) {
            return server.getId();
        }
        return inviteCode.trim().toUpperCase();
    }

    private void updateInviteServerButtonState() {
        if (contextInviteButton == null) {
            return;
        }
        boolean hasSelectedServer = selectedServer().isPresent();
        contextInviteButton.setDisable(
            dmHomeSelected || !service.isConnected() || service.currentUser().isEmpty() || !hasSelectedServer
        );
    }

    private void inviteDmPeerToServer(WorkspaceServer server, UserSummary target, Label feedbackLabel) {
        if (server == null || target == null) {
            return;
        }
        try {
            service.sendDm(target.getId(), inviteMessageFor(server));
            refreshDms();
            setInviteModalFeedback(feedbackLabel, "Invite sent to " + displayName(target) + ".", false);
        } catch (RuntimeException ex) {
            setInviteModalFeedback(feedbackLabel, rootMessage(ex), true);
        }
    }

    private String inviteMessageFor(WorkspaceServer server) {
        String inviterName = service.currentUser().map(this::displayName).orElse("Someone");
        return inviterName +
            " invited you to join \"" +
            server.getName() +
            "\".\nInvite ID: " +
            inviteCodeFor(server) +
            "\nOpen Join Server and paste this code.";
    }

    private void copyTextToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void setInviteModalFeedback(Label feedbackLabel, String message, boolean error) {
        if (feedbackLabel == null) {
            return;
        }
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("invite-modal-feedback-error", "invite-modal-feedback-success");
        feedbackLabel.getStyleClass().add(error ? "invite-modal-feedback-error" : "invite-modal-feedback-success");
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }

    private void createChannel() {
        if (selectedServerId == null) {
            showError("Select a server first");
            return;
        }
        WorkspaceServer server = selectedServer().orElse(null);
        if (!canManageServerAppearance(server)) {
            showError("Only owner/admin can create channels");
            return;
        }
        try {
            List<ChannelGroup> groups = service.listChannelGroups(selectedServerId);
            showCreateChannelModal(groups, (channelName, groupId) -> {
                try {
                    Channel channel = service.createChannel(selectedServerId, channelName, groupId);
                    selectedChannelId = channel.getId();
                    activeMode = ConversationMode.CHANNEL;
                    refreshChannels();
                    selectFirstChannelIfNeeded();
                    refreshMembers();
                    refreshMessages();
                    refreshHeader();
                } catch (RuntimeException ex) {
                    showError(rootMessage(ex));
                }
            });
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
        }
    }

    private void createChannelGroup() {
        if (selectedServerId == null) {
            showError("Select a server first");
            return;
        }
        WorkspaceServer server = selectedServer().orElse(null);
        if (!canManageServerAppearance(server)) {
            showError("Only owner/admin can create channel groups");
            return;
        }
        showTextInputModal(
            "Create Channel Group",
            "Organize channels into categories.",
            "Group Name",
            "Create",
            groupName -> {
                try {
                    service.createChannelGroup(selectedServerId, groupName);
                    refreshChannels();
                    refreshHeader();
                } catch (RuntimeException ex) {
                    showError(rootMessage(ex));
                }
            }
        );
    }

    private void openDm() {
        try {
            List<UserSummary> users = service.listUsers();
            if (users.isEmpty()) {
                showError("No users found");
                return;
            }
            showUserPickerModal("Start New DM", "Choose a user to message", users, this::startDmWithUser);
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
        }
    }

    private void startDmWithUser(UserSummary target) {
        if (target == null) {
            return;
        }
        if (service.currentUser().map(currentUser -> currentUser.getId().equals(target.getId())).orElse(false)) {
            return;
        }
        try {
            clearMessageEditState(false);
            service.openDm(target.getId());
            selectedDmUserId = target.getId();
            selectedServerId = null;
            selectedChannelId = null;
            dmHomeSelected = true;
            activeMode = ConversationMode.DM;
            withSuppressedSelections(() -> serverListView.getSelectionModel().clearSelection());
            updateDmHomeButtonState();
            refreshDms();
            withSuppressedSelections(() -> dmListView.getSelectionModel().clearSelection());
            dmListView.getItems().stream()
                .filter(user -> user.getId().equals(target.getId()))
                .findFirst()
                .ifPresent(user -> dmListView.getSelectionModel().select(user));
            updateContextSidebarMode();
            refreshMembers();
            refreshMessages();
            refreshHeader();
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
        }
    }

    private void sendMessage() {
        hideMentionSuggestions();
        String content = messageInput.getText() == null ? "" : messageInput.getText();
        List<MessageAttachment> attachments = List.copyOf(pendingComposerAttachments);
        if (content.isBlank() && attachments.isEmpty()) {
            return;
        }
        String replyToMessageId = composerReplyTarget == null ? null : composerReplyTarget.messageId();
        try {
            if (activeMode == ConversationMode.CHANNEL && selectedServerId != null && selectedChannelId != null) {
                service.sendMessage(selectedServerId, selectedChannelId, content, replyToMessageId, attachments);
            } else if (activeMode == ConversationMode.DM && selectedDmUserId != null) {
                UserProfileDetails profile = resolveSelectedDmProfile(false).orElse(null);
                if (profile != null && (profile.isBlockedByRequester() || profile.isBlockedRequester())) {
                    refreshComposerState();
                    return;
                }
                service.sendDm(selectedDmUserId, content, replyToMessageId, attachments);
            } else {
                showError("Select a channel or DM first");
                return;
            }
            messageInput.clear();
            clearComposerReply();
            clearComposerAttachments();
            stopLocalTyping();
            refreshComposerState();
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
        }
    }

    private void onComposerChanged(String newValue) {
        if (activeMode == ConversationMode.NONE) {
            return;
        }
        if (activeMode == ConversationMode.DM) {
            UserProfileDetails profile = resolveSelectedDmProfile(false).orElse(null);
            if (profile != null && (profile.isBlockedByRequester() || profile.isBlockedRequester())) {
                stopLocalTyping();
                return;
            }
        }

        if (newValue == null || newValue.isBlank()) {
            stopLocalTyping();
            return;
        }

        if (!localTypingActive) {
            try {
                if (activeMode == ConversationMode.CHANNEL && selectedServerId != null && selectedChannelId != null) {
                    service.sendTypingChannel(selectedServerId, selectedChannelId, true);
                    localTypingActive = true;
                } else if (activeMode == ConversationMode.DM && selectedDmUserId != null) {
                    service.sendTypingDm(selectedDmUserId, true);
                    localTypingActive = true;
                }
            } catch (RuntimeException ignored) {
            }
        }
        typingPause.playFromStart();
    }

    private void stopLocalTyping() {
        if (!localTypingActive) {
            typingPause.stop();
            return;
        }

        try {
            if (activeMode == ConversationMode.CHANNEL && selectedServerId != null && selectedChannelId != null) {
                service.sendTypingChannel(selectedServerId, selectedChannelId, false);
            } else if (activeMode == ConversationMode.DM && selectedDmUserId != null) {
                service.sendTypingDm(selectedDmUserId, false);
            }
        } catch (RuntimeException ignored) {
        } finally {
            localTypingActive = false;
            typingPause.stop();
        }
    }

    private void refreshSession() {
        String connectionStatus = service.isConnected() ? "Connected" : "Disconnected";
        String userStatus = service.currentUser().isPresent()
            ? " as " + service.currentDisplayName() + " (@" + service.currentUsername() + ")"
            : "";
        sessionLabel.setText(connectionStatus + userStatus);
        unreadLabel.setText("Unread: " + service.totalUnread());

        if (userBarNameLabel != null && userBarStatusLabel != null && userBarAvatarPane != null) {
            if (service.currentUser().isPresent()) {
                UserSummary user = service.currentUser().orElseThrow();
                userBarNameLabel.setText(displayName(user));
                userBarStatusLabel.setText(statusLabelForCurrentUser(user));
                refreshUserBarAvatar(user);
                refreshPresenceButtonIcon(user);
            } else {
                userBarNameLabel.setText("Guest");
                userBarStatusLabel.setText("Offline");
                refreshUserBarAvatar(null);
                refreshPresenceButtonIcon(null);
            }
        }

        updateDmHomeButtonState();
        updateRailUnreadDot(dmHomeButton, service.totalUnreadDms() > 0);
        updateContextSidebarMode();
    }

    private void refreshServers() {
        if (!service.isConnected() || service.currentUser().isEmpty()) {
            serverListView.getItems().clear();
            selectedServerId = null;
            selectedChannelId = null;
            dmHomeSelected = true;
            updateDmHomeButtonState();
            updateContextSidebarMode();
            refreshMembers();
            return;
        }
        try {
            List<WorkspaceServer> servers = service.listServers();
            String desiredServerId = selectedServerId;
            withSuppressedSelections(() -> {
                serverListView.getItems().setAll(servers);
                if (desiredServerId != null) {
                    serverListView.getItems().stream()
                        .filter(server -> server.getId().equals(desiredServerId))
                        .findFirst()
                        .ifPresentOrElse(
                            server -> serverListView.getSelectionModel().select(server),
                            () -> serverListView.getSelectionModel().clearSelection()
                        );
                } else {
                    serverListView.getSelectionModel().clearSelection();
                }
            });
            if (desiredServerId != null) {
                serverListView.getItems().stream()
                    .filter(server -> server.getId().equals(selectedServerId))
                    .findFirst()
                    .ifPresentOrElse(
                        server -> selectedServerId = server.getId(),
                        () -> selectedServerId = null
                    );
            } else {
                dmHomeSelected = true;
                selectedChannelId = null;
            }
            updateDmHomeButtonState();
            updateContextSidebarMode();
            refreshMembers();
        } catch (RuntimeException ex) {
            serverListView.getItems().clear();
            selectedServerId = null;
            selectedChannelId = null;
            dmHomeSelected = true;
            updateDmHomeButtonState();
            updateContextSidebarMode();
            refreshMembers();
        }
    }

    private void refreshChannels() {
        if (!service.isConnected() || service.currentUser().isEmpty() || selectedServerId == null) {
            channelListView.getItems().clear();
            loadedChannelGroups.clear();
            channelGroupById.clear();
            updateContextSidebarMode();
            return;
        }
        try {
            List<ChannelGroup> groups = service.listChannelGroups(selectedServerId);
            List<Channel> channels = service.listChannels(selectedServerId);
            loadedChannelGroups.clear();
            loadedChannelGroups.addAll(groups.stream().sorted(Comparator.comparingInt(ChannelGroup::getSortOrder)).toList());
            channelGroupById.clear();
            loadedChannelGroups.forEach(group -> channelGroupById.put(group.getId(), group));
            String desiredChannelId = selectedChannelId;
            withSuppressedSelections(() -> {
                applyGroupedChannelRows(channels);
                if (desiredChannelId != null) {
                    channelListView.getItems().stream()
                        .filter(item -> item instanceof Channel channel && channel.getId().equals(desiredChannelId))
                        .map(item -> (Channel) item)
                        .findFirst()
                        .ifPresentOrElse(
                            channel -> channelListView.getSelectionModel().select(channel),
                            () -> channelListView.getSelectionModel().clearSelection()
                        );
                } else {
                    channelListView.getSelectionModel().clearSelection();
                }
            });
            if (desiredChannelId != null) {
                boolean exists = channelListView.getItems().stream()
                    .anyMatch(item -> item instanceof Channel channel && channel.getId().equals(desiredChannelId));
                if (exists) {
                    selectedChannelId = desiredChannelId;
                } else {
                    selectedChannelId = null;
                    if (activeMode == ConversationMode.CHANNEL) {
                        activeMode = ConversationMode.NONE;
                    }
                }
            }
            channelListView.refresh();
            updateContextSidebarMode();
        } catch (RuntimeException ex) {
            channelListView.getItems().clear();
            loadedChannelGroups.clear();
            channelGroupById.clear();
            updateContextSidebarMode();
        }
    }

    private void applyGroupedChannelRows(List<Channel> channels) {
        Map<String, List<Channel>> channelsByGroupId = new HashMap<>();
        for (Channel channel : channels) {
            channelsByGroupId.computeIfAbsent(channel.getGroupId(), ignored -> new ArrayList<>()).add(channel);
        }

        List<Object> rows = new ArrayList<>();
        for (ChannelGroup group : loadedChannelGroups) {
            List<Channel> groupChannels = channelsByGroupId.getOrDefault(group.getId(), List.of())
                .stream()
                .sorted(Comparator.comparing(Channel::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
            boolean collapsed = collapsedChannelGroupIds.contains(group.getId());
            rows.add(new ChannelGroupHeaderRow(group, collapsed, groupChannels.size()));
            if (!collapsed) {
                rows.addAll(groupChannels);
            }
        }

        channelListView.getItems().setAll(rows);
    }

    private void toggleChannelGroupCollapse(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return;
        }
        if (collapsedChannelGroupIds.contains(groupId)) {
            collapsedChannelGroupIds.remove(groupId);
        } else {
            collapsedChannelGroupIds.add(groupId);
        }
        refreshChannels();
    }

    private void selectFirstChannelIfNeeded() {
        if (selectedServerId == null) {
            return;
        }

        if (selectedChannelId != null) {
            Optional<Channel> existingSelection = channelListView.getItems().stream()
                .filter(item -> item instanceof Channel channel && channel.getId().equals(selectedChannelId))
                .map(item -> (Channel) item)
                .findFirst();
            if (existingSelection.isPresent()) {
                channelListView.getSelectionModel().select(existingSelection.get());
                return;
            }
        }

        Optional<Channel> firstChannel = channelListView.getItems().stream()
            .filter(item -> item instanceof Channel)
            .map(item -> (Channel) item)
            .findFirst();
        if (firstChannel.isPresent()) {
            channelListView.getSelectionModel().select(firstChannel.get());
            return;
        }

        selectedChannelId = null;
        if (activeMode == ConversationMode.CHANNEL) {
            activeMode = ConversationMode.NONE;
        }
        service.clearActiveConversation();
        refreshMessages();
        refreshHeader();
        refreshMembers();
    }

    private void refreshDms() {
        if (!service.isConnected() || service.currentUser().isEmpty()) {
            dmListView.getItems().clear();
            clearSelectedDmProfile();
            updateContextSidebarMode();
            return;
        }
        try {
            List<UserSummary> peers = service.listDmPeers();
            String desiredDmUserId = selectedDmUserId;
            withSuppressedSelections(() -> {
                dmListView.getItems().setAll(peers);
                if (desiredDmUserId != null) {
                    dmListView.getItems().stream()
                        .filter(peer -> peer.getId().equals(desiredDmUserId))
                        .findFirst()
                        .ifPresentOrElse(
                            peer -> dmListView.getSelectionModel().select(peer),
                            () -> dmListView.getSelectionModel().clearSelection()
                        );
                } else {
                    dmListView.getSelectionModel().clearSelection();
                }
            });
            if (desiredDmUserId != null) {
                dmListView.getItems().stream()
                    .filter(peer -> peer.getId().equals(selectedDmUserId))
                    .findFirst()
                    .ifPresentOrElse(
                        peer -> selectedDmUserId = peer.getId(),
                        () -> {
                            selectedDmUserId = null;
                            clearSelectedDmProfile();
                            if (activeMode == ConversationMode.DM) {
                                activeMode = ConversationMode.NONE;
                            }
                        }
                    );
            }
            dmListView.refresh();
            updateContextSidebarMode();
        } catch (RuntimeException ex) {
            dmListView.getItems().clear();
            clearSelectedDmProfile();
            updateContextSidebarMode();
        }
    }

    private void refreshMembers() {
        if (memberPane == null) {
            return;
        }

        boolean shouldShowDmProfile =
            service.isConnected() &&
                service.currentUser().isPresent() &&
                activeMode == ConversationMode.DM &&
                selectedDmUserId != null;

        if (shouldShowDmProfile) {
            Optional<UserProfileDetails> profile = resolveSelectedDmProfile(false);
            if (profile.isPresent()) {
                populateDmProfilePane(profile.orElseThrow());
                showDmProfilePane();
            } else {
                clearMemberPaneState();
            }
            return;
        }

        boolean shouldShowMembers =
            service.isConnected() &&
                service.currentUser().isPresent() &&
                selectedServerId != null &&
                selectedChannelId != null &&
                !dmHomeSelected;

        if (!shouldShowMembers) {
            clearMemberPaneState();
            return;
        }

        try {
            allChannelMembers.clear();
            allChannelMembers.addAll(service.listChannelMembers(selectedServerId, selectedChannelId));
            sortChannelMembersByPresence();
            applyMemberFilter();
            messageListView.refresh();
            if (memberHeaderLabel != null) {
                memberHeaderLabel.setText("MEMBERS \u2014 " + allChannelMembers.size());
            }
            showChannelMembersPane();
        } catch (RuntimeException ex) {
            clearMemberPaneState();
        }
    }

    private Optional<UserProfileDetails> resolveSelectedDmProfile(boolean forceRefresh) {
        if (!service.isConnected() || service.currentUser().isEmpty() || selectedDmUserId == null) {
            selectedDmProfile = null;
            return Optional.empty();
        }

        if (!forceRefresh) {
            if (selectedDmProfile != null && selectedDmUserId.equals(selectedDmProfile.getId())) {
                return Optional.of(selectedDmProfile);
            }
            UserProfileDetails cached = dmProfileCache.get(selectedDmUserId);
            if (cached != null) {
                selectedDmProfile = cached;
                return Optional.of(cached);
            }
        }

        try {
            UserProfileDetails profile = service.getUserProfile(selectedDmUserId);
            selectedDmProfile = profile;
            if (profile != null) {
                dmProfileCache.put(profile.getId(), profile);
            }
            return Optional.ofNullable(profile);
        } catch (RuntimeException ex) {
            selectedDmProfile = null;
            return Optional.empty();
        }
    }

    private void populateDmProfilePane(UserProfileDetails profile) {
        if (dmProfileContent == null) {
            return;
        }

        Label presence = new Label();
        presence.getStyleClass().addAll("presence-dot", presenceStyleClass(profile));

        StackPane avatarWrap = new StackPane(createAvatarGraphic(profile, "dm-profile-avatar", 88), presence);
        avatarWrap.getStyleClass().add("dm-profile-avatar-wrap");
        StackPane.setAlignment(presence, Pos.BOTTOM_RIGHT);

        Label name = new Label(displayName(profile));
        name.getStyleClass().add("dm-profile-name");

        Label handle = new Label("@" + profile.getUsername());
        handle.getStyleClass().add("dm-profile-handle");

        Label status = new Label(statusLabelFor(profile));
        status.getStyleClass().add("dm-profile-status");

        VBox heroMeta = new VBox(4, name, handle, status);
        heroMeta.setAlignment(Pos.CENTER_LEFT);

        HBox hero = new HBox(14, avatarWrap, heroMeta);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("dm-profile-hero");

        VBox accountCard = createProfileSectionCard(
            "ACCOUNT",
            createProfileFactRow("Registered since", registeredSinceLabel(profile)),
            createProfileFactRow("Current status", statusLabelFor(profile)),
            createProfileFactRow("Username", "@" + profile.getUsername())
        );

        Button blockToggleButton = createActionChip(
            profile.isBlockedByRequester() ? "Unblock" : "Block",
            profile.isBlockedByRequester() ? "fas-user-check" : "fas-user-slash",
            profile.isBlockedByRequester() ? "action-chip-neutral" : "action-chip-danger"
        );
        blockToggleButton.setOnAction(event -> {
            boolean blocking = !profile.isBlockedByRequester();
            String display = displayName(profile);
            if (blocking && !confirmAction("Block User", "Block " + display + "?", "You will stop receiving new DMs until you unblock them.")) {
                return;
            }
            try {
                if (blocking) {
                    service.blockUser(profile.getId());
                } else {
                    service.unblockUser(profile.getId());
                }
                invalidateDmProfileCache();
                refreshChatViews();
            } catch (RuntimeException ex) {
                showError(rootMessage(ex));
            }
        });

        Node dmActionRow = createProfileActionRow(
            profile.isBlockedByRequester() ? "Unblock user" : "Block user",
            "",
            profile.isBlockedByRequester() ? "fas-user-check" : "fas-comment-slash",
            profile.isBlockedByRequester() ? "profile-action-row-neutral" : "profile-action-row-danger",
            blockToggleButton
        );

        Button deleteChatButton = createActionChip("Delete Chat", "fas-trash-alt", "action-chip-danger");
        deleteChatButton.setOnAction(event -> {
            if (!confirmAction(
                "Delete Chat",
                "Delete this DM conversation?",
                "This removes the chat history for both people."
            )) {
                return;
            }
            try {
                service.deleteDmConversation(profile.getId());
                selectedDmUserId = null;
                activeMode = ConversationMode.NONE;
                clearSelectedDmProfile();
                invalidateDmProfileCache();
                withSuppressedSelections(() -> dmListView.getSelectionModel().clearSelection());
                refreshChatViews();
            } catch (RuntimeException ex) {
                showError(rootMessage(ex));
            }
        });

        Node deleteChatRow = createProfileActionRow(
            "Delete chat",
            "",
            "fas-trash-alt",
            "profile-action-row-danger",
            deleteChatButton
        );

        VBox dmActionsCard = createProfileSectionCard("DIRECT MESSAGE", dmActionRow, deleteChatRow);

        VBox mutualContent = new VBox(8);
        mutualContent.getStyleClass().add("profile-list");
        if (profile.getMutualServerNames().isEmpty()) {
            Label empty = new Label("No shared servers yet.");
            empty.getStyleClass().add("profile-empty-text");
            empty.setWrapText(true);
            mutualContent.getChildren().add(empty);
        } else {
            for (String serverName : profile.getMutualServerNames()) {
                Label serverLabel = new Label(serverName);
                serverLabel.getStyleClass().add("profile-list-item");
                mutualContent.getChildren().add(serverLabel);
            }
        }

        Label mutualSummary = new Label(mutualServersLabel(profile));
        mutualSummary.getStyleClass().add("profile-section-subtitle");
        mutualSummary.setWrapText(true);

        VBox mutualCard = createProfileSectionCard("MUTUAL SERVERS", mutualSummary, mutualContent);

        dmProfileContent.getChildren().setAll(hero, accountCard, dmActionsCard, mutualCard);
    }

    private VBox createProfileSectionCard(String title, Node... content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("profile-section-title");

        VBox card = new VBox(10, titleLabel);
        card.getStyleClass().add("profile-section-card");
        card.getChildren().addAll(content);
        return card;
    }

    private Node createProfileActionRow(
        String titleText,
        String descriptionText,
        String iconLiteral,
        String toneStyleClass,
        Node... actions
    ) {
        FontIcon icon = createIcon(iconLiteral, "profile-action-icon");
        StackPane iconWrap = new StackPane(icon);
        iconWrap.getStyleClass().addAll("profile-action-icon-wrap", toneStyleClass);

        Label title = new Label(titleText);
        title.getStyleClass().add("profile-action-title");

        VBox meta = new VBox(4, title);
        if (descriptionText != null && !descriptionText.isBlank()) {
            Label description = new Label(descriptionText);
            description.getStyleClass().add("profile-action-description");
            description.setWrapText(true);
            meta.getChildren().add(description);
        }
        meta.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        actionBox.getChildren().addAll(actions);

        HBox row = new HBox(12, iconWrap, meta, spacer, actionBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-action-row");
        return row;
    }

    private Button createActionChip(String text, String iconLiteral, String toneStyleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("action-chip", toneStyleClass);
        if (iconLiteral != null && !iconLiteral.isBlank()) {
            button.setGraphic(createIcon(iconLiteral, "action-chip-icon"));
        }
        return button;
    }

    private VBox createProfileFactRow(String labelText, String valueText) {
        Label label = new Label(labelText);
        label.getStyleClass().add("profile-fact-label");

        Label value = new Label(valueText);
        value.getStyleClass().add("profile-fact-value");
        value.setWrapText(true);

        VBox row = new VBox(3, label, value);
        row.getStyleClass().add("profile-fact-row");
        return row;
    }

    private void showChannelMembersPane() {
        if (channelMemberPane != null) {
            channelMemberPane.setVisible(true);
            channelMemberPane.setManaged(true);
        }
        if (dmProfilePane != null) {
            dmProfilePane.setVisible(false);
            dmProfilePane.setManaged(false);
        }
        updateMemberPaneVisibility(true);
    }

    private void showDmProfilePane() {
        if (channelMemberPane != null) {
            channelMemberPane.setVisible(false);
            channelMemberPane.setManaged(false);
        }
        if (dmProfilePane != null) {
            dmProfilePane.setVisible(true);
            dmProfilePane.setManaged(true);
        }
        updateMemberPaneVisibility(true);
    }

    private void clearMemberPaneState() {
        allChannelMembers.clear();
        memberListView.getItems().clear();
        memberSearchField.clear();
        if (memberHeaderLabel != null) {
            memberHeaderLabel.setText("MEMBERS");
        }
        if (dmProfileContent != null) {
            dmProfileContent.getChildren().clear();
        }
        if (channelMemberPane != null) {
            channelMemberPane.setVisible(false);
            channelMemberPane.setManaged(false);
        }
        if (dmProfilePane != null) {
            dmProfilePane.setVisible(false);
            dmProfilePane.setManaged(false);
        }
        updateMemberPaneVisibility(false);
    }

    private void clearSelectedDmProfile() {
        selectedDmProfile = null;
    }

    private void invalidateDmProfileCache() {
        selectedDmProfile = null;
        dmProfileCache.clear();
    }

    private void updateMemberPaneVisibility(boolean visible) {
        memberPane.setVisible(visible);
        memberPane.setManaged(visible);
        if (messageMemberSplit != null) {
            if (visible) {
                double current = messageMemberSplit.getDividerPositions().length > 0
                    ? messageMemberSplit.getDividerPositions()[0]
                    : 0.76;
                if (current >= 0.98) {
                    messageMemberSplit.setDividerPositions(0.76);
                }
            } else {
                messageMemberSplit.setDividerPositions(1.0);
            }
        }
    }

    private void applyMemberFilter() {
        String query = memberSearchField.getText() == null ? "" : memberSearchField.getText().trim().toLowerCase();
        List<UserSummary> filteredMembers = query.isBlank()
            ? new ArrayList<>(allChannelMembers)
            : allChannelMembers.stream()
                .filter(member -> profileSearchText(member).contains(query))
                .toList();
        List<Object> rows = new ArrayList<>();
        List<UserSummary> onlineMembers = filteredMembers.stream().filter(UserSummary::isOnline).toList();
        List<UserSummary> offlineMembers = filteredMembers.stream().filter(member -> !member.isOnline()).toList();
        if (!onlineMembers.isEmpty()) {
            rows.add(new MemberSectionHeader("ONLINE", onlineMembers.size()));
            rows.addAll(onlineMembers);
        }
        if (!offlineMembers.isEmpty()) {
            rows.add(new MemberSectionHeader("OFFLINE", offlineMembers.size()));
            rows.addAll(offlineMembers);
        }
        memberListView.getItems().setAll(rows);
        memberListView.refresh();
    }

    private void prepareUnreadBoundaryForChannel(String serverId, String channelId) {
        int unread = service.unreadCountChannel(serverId, channelId);
        if (unread <= 0) {
            clearUnreadBoundary();
            return;
        }
        unreadBoundaryChannelKey = serverId + ":" + channelId;
        unreadBoundaryDmUserId = null;
        unreadBoundaryCount = unread;
        unreadBoundaryPending = true;
    }

    private void prepareUnreadBoundaryForDm(String userId) {
        int unread = service.unreadCountDm(userId);
        if (unread <= 0) {
            clearUnreadBoundary();
            return;
        }
        unreadBoundaryChannelKey = null;
        unreadBoundaryDmUserId = userId;
        unreadBoundaryCount = unread;
        unreadBoundaryPending = true;
    }

    private void clearUnreadBoundary() {
        unreadBoundaryChannelKey = null;
        unreadBoundaryDmUserId = null;
        unreadBoundaryCount = 0;
        unreadBoundaryPending = false;
    }

    private int unreadBoundaryCountForCurrentConversation() {
        if (!unreadBoundaryPending || unreadBoundaryCount <= 0) {
            return 0;
        }
        if (activeMode == ConversationMode.CHANNEL && selectedServerId != null && selectedChannelId != null) {
            String currentKey = selectedServerId + ":" + selectedChannelId;
            return currentKey.equals(unreadBoundaryChannelKey) ? unreadBoundaryCount : 0;
        }
        if (activeMode == ConversationMode.DM && selectedDmUserId != null) {
            return selectedDmUserId.equals(unreadBoundaryDmUserId) ? unreadBoundaryCount : 0;
        }
        return 0;
    }

    private void consumeUnreadBoundary() {
        int boundaryCount = unreadBoundaryCountForCurrentConversation();
        if (boundaryCount <= 0) {
            return;
        }
        if (activeMode == ConversationMode.CHANNEL && selectedServerId != null && selectedChannelId != null) {
            service.markChannelRead(selectedServerId, selectedChannelId);
        } else if (activeMode == ConversationMode.DM && selectedDmUserId != null) {
            service.markDmRead(selectedDmUserId);
        }
        clearUnreadBoundary();
    }

    private void markVisibleConversationReadIfNeeded() {
        try {
            if (activeMode == ConversationMode.CHANNEL && selectedServerId != null && selectedChannelId != null) {
                if (service.unreadCountChannel(selectedServerId, selectedChannelId) > 0) {
                    service.markChannelRead(selectedServerId, selectedChannelId);
                }
            } else if (activeMode == ConversationMode.DM && selectedDmUserId != null) {
                if (service.unreadCountDm(selectedDmUserId) > 0) {
                    service.markDmRead(selectedDmUserId);
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void refreshMessages() {
        updateMessagePlaceholder();
        if (!service.isConnected() || service.currentUser().isEmpty()) {
            messageListView.getItems().clear();
            renderedMessageCount = 0;
            clearMessageEditState(false);
            pendingMessageScrollId = null;
            return;
        }

        int previousCount = renderedMessageCount;
        try {
            int unreadBoundary = unreadBoundaryCountForCurrentConversation();
            String anchorMessageId = scrollAnchorMessageId();
            if (activeMode == ConversationMode.CHANNEL && selectedServerId != null && selectedChannelId != null) {
                List<ChatMessage> messages = service.listMessages(selectedServerId, selectedChannelId);
                syncMessageEditState(messages);
                messageListView.getItems().setAll(
                    buildConversationRows(
                        selectedServer().orElse(null),
                        selectedChannel().orElse(null),
                        messages,
                        unreadBoundary
                    )
                );
            } else if (activeMode == ConversationMode.DM && selectedDmUserId != null) {
                List<DirectMessage> messages = service.listDmMessages(selectedDmUserId);
                syncMessageEditState(messages);
                UserProfileDetails profile = resolveSelectedDmProfile(false).orElse(null);
                messageListView.getItems().setAll(buildConversationRowsForDms(messages, profile, unreadBoundary));
            } else {
                clearMessageEditState(false);
                messageListView.getItems().clear();
            }
            if (!messageListView.getItems().isEmpty()) {
                int anchorIndex = messageIndexForId(anchorMessageId);
                if (anchorIndex >= 0) {
                    messageListView.scrollTo(Math.max(0, anchorIndex - 1));
                } else if (unreadBoundary > 0) {
                    int dividerIndex = messageListView.getItems().indexOf(new ConversationUnreadDivider("New"));
                    if (dividerIndex >= 0) {
                        messageListView.scrollTo(Math.max(0, dividerIndex));
                    } else {
                        messageListView.scrollTo(messageListView.getItems().size() - 1);
                    }
                } else {
                    messageListView.scrollTo(messageListView.getItems().size() - 1);
                }
            }
            pendingMessageScrollId = null;
            renderedMessageCount = messageListView.getItems().size();
            if (renderedMessageCount > previousCount) {
                animateMessageListPulse();
            }
            consumeUnreadBoundary();
            markVisibleConversationReadIfNeeded();
        } catch (RuntimeException ex) {
            messageListView.getItems().clear();
            renderedMessageCount = 0;
            pendingMessageScrollId = null;
        }
    }

    private void refreshHeader() {
        if (!service.isConnected() || service.currentUser().isEmpty()) {
            channelLabel.setText("Connect and choose a conversation to start chatting.");
            typingLabel.setText("");
            refreshComposerState();
            return;
        }

        if (activeMode == ConversationMode.CHANNEL && selectedServerId != null && selectedChannelId != null) {
            channelListView.getItems().stream()
                .filter(item -> item instanceof Channel channel && channel.getId().equals(selectedChannelId))
                .map(item -> (Channel) item)
                .findFirst()
                .ifPresentOrElse(
                    channel -> channelLabel.setText("Channel: #" + channel.getName()),
                    () -> channelLabel.setText("No channel selected")
            );
            typingLabel.setText(service.typingTextForChannel(selectedServerId, selectedChannelId));
            refreshComposerState();
            return;
        }

        if (activeMode == ConversationMode.DM && selectedDmUserId != null) {
            String username = service.knownUser(selectedDmUserId)
                .map(UserSummary::displayName)
                .orElse("Unknown");
            channelLabel.setText("DM: @" + username);
            typingLabel.setText(service.typingTextForDm(selectedDmUserId));
            refreshComposerState();
            return;
        }

        channelLabel.setText(
            (dmHomeSelected || sidebarDmVisible || selectedServerId == null)
                ? "No DM selected"
                : "No channel selected"
        );
        typingLabel.setText("");
        refreshComposerState();
    }

    private void refreshComposerState() {
        if (composerBar == null || composerBlockedPane == null || composerBlockedLabel == null || composerBlockedActionButton == null) {
            return;
        }

        String conversationKey = currentConversationKey();
        if (composerAttachmentConversationKey != null && !Objects.equals(composerAttachmentConversationKey, conversationKey)) {
            clearComposerAttachments();
        }
        refreshComposerReplyUi();
        boolean canCompose = service.isConnected() && service.currentUser().isPresent();
        boolean hasActiveConversation =
            (activeMode == ConversationMode.CHANNEL && selectedServerId != null && selectedChannelId != null) ||
                (activeMode == ConversationMode.DM && selectedDmUserId != null);
        boolean showBlockedPane = false;
        String blockedText = null;
        Runnable blockedAction = null;
        String actionText = null;

        if (canCompose && activeMode == ConversationMode.DM && selectedDmUserId != null) {
            UserProfileDetails profile = resolveSelectedDmProfile(false).orElse(null);
            if (profile != null) {
                if (profile.isBlockedByRequester() && profile.isBlockedRequester()) {
                    showBlockedPane = true;
                    blockedText = "You blocked this user and they blocked you. Tap to unblock your side.";
                    actionText = "Unblock";
                    blockedAction = () -> handleDmUnblock(profile);
                } else if (profile.isBlockedByRequester()) {
                    showBlockedPane = true;
                    blockedText = "You blocked this user. Tap to unblock and resume chatting.";
                    actionText = "Unblock";
                    blockedAction = () -> handleDmUnblock(profile);
                } else if (profile.isBlockedRequester()) {
                    showBlockedPane = true;
                    blockedText = "This user blocked you. You can view message history, but you cannot send new messages.";
                }
            }
        }

        if (showBlockedPane) {
            stopLocalTyping();
            messageInput.clear();
            clearComposerReply();
            clearComposerAttachments();
        }

        if (!canCompose || !hasActiveConversation || showBlockedPane) {
            hideMentionSuggestions();
        }

        boolean showComposer = canCompose && hasActiveConversation && !showBlockedPane;
        composerBar.setVisible(showComposer);
        composerBar.setManaged(showComposer);
        if (composerAttachmentPane != null) {
            boolean showAttachments = showComposer && !pendingComposerAttachments.isEmpty();
            composerAttachmentPane.setVisible(showAttachments);
            composerAttachmentPane.setManaged(showAttachments);
        }
        composerBlockedPane.setVisible(hasActiveConversation && showBlockedPane);
        composerBlockedPane.setManaged(hasActiveConversation && showBlockedPane);
        composerBlockedLabel.setText(blockedText == null ? "" : blockedText);

        if (blockedAction != null) {
            Runnable finalBlockedAction = blockedAction;
            composerBlockedActionButton.setText(actionText == null ? "Action" : actionText);
            composerBlockedActionButton.setVisible(true);
            composerBlockedActionButton.setManaged(true);
            composerBlockedActionButton.setOnAction(event -> finalBlockedAction.run());
        } else {
            composerBlockedActionButton.setVisible(false);
            composerBlockedActionButton.setManaged(false);
            composerBlockedActionButton.setOnAction(null);
        }
    }

    private void handleDmUnblock(UserProfileDetails profile) {
        if (profile == null) {
            return;
        }
        try {
            service.unblockUser(profile.getId());
            invalidateDmProfileCache();
            refreshChatViews();
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
        }
    }

    private void showEditProfileModal() {
        if (pageContainer == null || service.currentUser().isEmpty()) {
            return;
        }

        UserSummary currentUser = service.currentUser().orElseThrow();
        final String[] selectedAvatarBase64 = new String[] {currentUser.getProfileImageBase64()};

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");

        Label titleLabel = new Label("Edit Profile");
        titleLabel.getStyleClass().add("modal-title");
        Label subtitleLabel = new Label("Update your display name, avatar, and password.");
        subtitleLabel.getStyleClass().add("modal-subtitle");

        Label nameLabel = new Label("DISPLAY NAME");
        nameLabel.getStyleClass().add("modal-field-label");
        TextField nameField = new TextField(displayName(currentUser));
        nameField.getStyleClass().add("modal-input");

        Label usernameLabel = new Label("USERNAME");
        usernameLabel.getStyleClass().add("modal-field-label");
        TextField usernameField = new TextField(currentUser.getUsername());
        usernameField.getStyleClass().addAll("modal-input", "modal-readonly");
        usernameField.setEditable(false);
        usernameField.setFocusTraversable(false);

        StackPane avatarPreview = new StackPane();
        avatarPreview.getStyleClass().add("profile-avatar-preview");
        Runnable refreshAvatarPreview = () ->
            avatarPreview.getChildren().setAll(
                createAvatarGraphic(
                    nameField.getText() == null || nameField.getText().isBlank() ? currentUser.getUsername() : nameField.getText(),
                    selectedAvatarBase64[0],
                    "profile-avatar-large",
                    88
                )
            );
        refreshAvatarPreview.run();
        nameField.textProperty().addListener((obs, oldValue, newValue) -> refreshAvatarPreview.run());

        Button chooseImageButton = new Button("Choose Picture");
        chooseImageButton.getStyleClass().addAll("modal-button", "modal-button-primary");
        Button removeImageButton = new Button("Remove Picture");
        removeImageButton.getStyleClass().addAll("modal-button", "modal-button-cancel");

        Label currentPasswordLabel = new Label("CURRENT PASSWORD");
        currentPasswordLabel.getStyleClass().add("modal-field-label");
        PasswordField currentPasswordField = new PasswordField();
        currentPasswordField.getStyleClass().add("modal-input");
        currentPasswordField.setPromptText("Required only when changing password");

        Label newPasswordLabel = new Label("NEW PASSWORD");
        newPasswordLabel.getStyleClass().add("modal-field-label");
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.getStyleClass().add("modal-input");
        newPasswordField.setPromptText("Leave blank to keep current password");

        Label confirmPasswordLabel = new Label("CONFIRM NEW PASSWORD");
        confirmPasswordLabel.getStyleClass().add("modal-field-label");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.getStyleClass().add("modal-input");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("modal-error");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        chooseImageButton.setOnAction(event -> {
            try {
                String nextAvatar = chooseImageBase64("Choose Profile Picture", PROFILE_IMAGE_MAX_BYTES, "Profile picture");
                if (nextAvatar != null) {
                    selectedAvatarBase64[0] = nextAvatar;
                    errorLabel.setVisible(false);
                    errorLabel.setManaged(false);
                    refreshAvatarPreview.run();
                }
            } catch (IllegalArgumentException | IOException ex) {
                errorLabel.setText(rootMessage(ex));
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
            }
        });

        removeImageButton.setOnAction(event -> {
            selectedAvatarBase64[0] = null;
            refreshAvatarPreview.run();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("modal-button", "modal-button-cancel");
        cancelButton.setOnAction(event -> pageContainer.getChildren().remove(overlay));

        Button saveButton = new Button("Save Changes");
        saveButton.getStyleClass().addAll("modal-button", "modal-button-primary");
        Runnable submitAction = () -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            String currentPassword = currentPasswordField.getText();
            String newPassword = newPasswordField.getText();
            String confirmPassword = confirmPasswordField.getText();

            if (name.isBlank()) {
                errorLabel.setText("Display name is required.");
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
                return;
            }

            if ((newPassword != null && !newPassword.isBlank()) || (confirmPassword != null && !confirmPassword.isBlank())) {
                if (currentPassword == null || currentPassword.isBlank()) {
                    errorLabel.setText("Current password is required to change password.");
                    errorLabel.setManaged(true);
                    errorLabel.setVisible(true);
                    return;
                }
                if (!newPassword.equals(confirmPassword)) {
                    errorLabel.setText("New password and confirmation do not match.");
                    errorLabel.setManaged(true);
                    errorLabel.setVisible(true);
                    return;
                }
            }

            try {
                service.updateProfile(
                    name,
                    currentUser.getStatus(),
                    currentPassword,
                    newPassword,
                    selectedAvatarBase64[0]
                );
                pageContainer.getChildren().remove(overlay);
            } catch (RuntimeException ex) {
                errorLabel.setText(rootMessage(ex));
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
            }
        };
        saveButton.setOnAction(event -> submitAction.run());
        confirmPasswordField.setOnAction(event -> submitAction.run());

        Label dangerTitle = new Label("DANGER ZONE");
        dangerTitle.getStyleClass().add("profile-section-title");

        Label dangerText = new Label("Delete your profile, remove your account access, and log out this user.");
        dangerText.getStyleClass().add("profile-section-subtitle");
        dangerText.setWrapText(true);

        Button deleteAccountButton = new Button("Delete Account");
        deleteAccountButton.getStyleClass().addAll("modal-button", "modal-button-danger");
        deleteAccountButton.setOnAction(event -> {
            String currentPassword = currentPasswordField.getText();
            if (currentPassword == null || currentPassword.isBlank()) {
                errorLabel.setText("Current password is required to delete your account.");
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
                return;
            }
            if (!confirmAction(
                "Delete Account",
                "Delete your account permanently?",
                "This removes your login profile. Owned servers will also be deleted."
            )) {
                return;
            }
            try {
                service.deleteAccount(currentPassword);
                pageContainer.getChildren().remove(overlay);
                clearAuthErrors();
                switchPage(AppPage.WELCOME, true);
            } catch (RuntimeException ex) {
                errorLabel.setText(rootMessage(ex));
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
            }
        });

        HBox avatarButtons = new HBox(10, chooseImageButton, removeImageButton);
        avatarButtons.getStyleClass().add("profile-avatar-actions");

        VBox dangerZone = new VBox(8, dangerTitle, dangerText, deleteAccountButton);
        dangerZone.getStyleClass().add("profile-section-card");

        HBox actions = new HBox(10, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("modal-actions");

        VBox card = new VBox(
            12,
            titleLabel,
            subtitleLabel,
            avatarPreview,
            avatarButtons,
            nameLabel,
            nameField,
            usernameLabel,
            usernameField,
            currentPasswordLabel,
            currentPasswordField,
            newPasswordLabel,
            newPasswordField,
            confirmPasswordLabel,
            confirmPasswordField,
            dangerZone,
            errorLabel,
            actions
        );
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(520);
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().add(card);
        pageContainer.getChildren().add(overlay);
        Platform.runLater(nameField::requestFocus);
    }

    private void showServerSettingsModal() {
        if (pageContainer == null) {
            return;
        }

        Optional<WorkspaceServer> selected = selectedServer();
        if (selected.isEmpty()) {
            showError("Select a server first");
            return;
        }

        WorkspaceServer server = selected.orElseThrow();
        boolean canManage = canManageServerAppearance(server);
        boolean owner = isCurrentUserServerOwner(server);

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");

        Label titleLabel = new Label("Server Settings");
        titleLabel.getStyleClass().add("modal-title");
        Label subtitleLabel = new Label("Manage " + server.getName() + " and your membership.");
        subtitleLabel.getStyleClass().add("modal-subtitle");

        VBox content = new VBox(12);
        content.getChildren().add(
            createProfileSectionCard(
                "SERVER",
                createProfileFactRow("Name", server.getName()),
                createProfileFactRow("Invite ID", inviteCodeFor(server)),
                createProfileFactRow("Your role", serverRoleLabel(server, currentUserId()))
            )
        );

        if (canManage) {
            Label adminSummary = new Label("Admins can manage appearance, members, bans, and delete this server.");
            adminSummary.getStyleClass().add("profile-section-subtitle");
            adminSummary.setWrapText(true);

            Button appearanceButton = createActionChip("Appearance", "fas-image", "action-chip-primary");
            appearanceButton.setOnAction(event -> {
                pageContainer.getChildren().remove(overlay);
                showServerAppearanceModal();
            });

            Button moderationButton = createActionChip("Members", "fas-user-shield", "action-chip-primary");
            moderationButton.setOnAction(event -> {
                pageContainer.getChildren().remove(overlay);
                showServerModerationModal();
            });

            Button deleteServerButton = createActionChip("Delete", "fas-trash-alt", "action-chip-danger");
            deleteServerButton.setOnAction(event -> {
                if (!confirmAction(
                    "Delete Server",
                    "Delete " + server.getName() + "?",
                    "This removes the server for every member."
                )) {
                    return;
                }
                try {
                    service.deleteServer(server.getId());
                    pageContainer.getChildren().remove(overlay);
                    selectDmHome();
                } catch (RuntimeException ex) {
                    showError(rootMessage(ex));
                }
            });

            Node appearanceRow = createProfileActionRow(
                "Server appearance",
                "Update the server icon and cover banner.",
                "fas-image",
                "profile-action-row-primary",
                appearanceButton
            );
            Node moderationRow = createProfileActionRow(
                "Member moderation",
                "Kick, ban, and unban server members.",
                "fas-user-shield",
                "profile-action-row-primary",
                moderationButton
            );
            Node deleteRow = createProfileActionRow(
                "Delete server",
                "Remove this server for everyone in it.",
                "fas-trash-alt",
                "profile-action-row-danger",
                deleteServerButton
            );
            content.getChildren().add(createProfileSectionCard("ADMIN", adminSummary, appearanceRow, moderationRow, deleteRow));
        }

        Label membershipSummary = new Label(
            owner
                ? "Owners cannot leave a server. Delete it instead if you no longer want it."
                : "Leave this server and remove it from your sidebar."
        );
        membershipSummary.getStyleClass().add("profile-section-subtitle");
        membershipSummary.setWrapText(true);

        Button leaveServerButton = createActionChip("Leave", "fas-sign-out-alt", "action-chip-neutral");
        leaveServerButton.setDisable(owner);
        leaveServerButton.setOnAction(event -> {
            if (!confirmAction(
                "Leave Server",
                "Leave " + server.getName() + "?",
                "You will need an invite ID to join again."
            )) {
                return;
            }
            try {
                service.leaveServer(server.getId());
                pageContainer.getChildren().remove(overlay);
                selectDmHome();
            } catch (RuntimeException ex) {
                showError(rootMessage(ex));
            }
        });

        Node leaveRow = createProfileActionRow(
            "Leave server",
            membershipSummary.getText(),
            "fas-sign-out-alt",
            "profile-action-row-neutral",
            leaveServerButton
        );
        content.getChildren().add(createProfileSectionCard("MEMBERSHIP", leaveRow));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("dm-profile-scroll");
        scrollPane.setPrefViewportHeight(420);

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().addAll("modal-button", "modal-button-cancel");
        closeButton.setOnAction(event -> pageContainer.getChildren().remove(overlay));

        HBox actions = new HBox(10, closeButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("modal-actions");

        VBox card = new VBox(12, titleLabel, subtitleLabel, scrollPane, actions);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(560);
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().add(card);
        pageContainer.getChildren().add(overlay);
        Platform.runLater(closeButton::requestFocus);
    }

    private void showServerModerationModal() {
        if (pageContainer == null) {
            return;
        }

        Optional<WorkspaceServer> selected = selectedServer();
        if (selected.isEmpty()) {
            showError("Select a server first");
            return;
        }

        WorkspaceServer server = selected.orElseThrow();
        if (!canManageServerAppearance(server)) {
            showError("Only owner/admin can manage server members");
            return;
        }

        final List<UserSummary> members;
        final List<UserSummary> bannedUsers;
        try {
            members = service.listServerMembers(server.getId());
            bannedUsers = service.listBannedUsers(server.getId());
        } catch (RuntimeException ex) {
            showError(rootMessage(ex));
            return;
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");

        Label titleLabel = new Label("Manage Members");
        titleLabel.getStyleClass().add("modal-title");
        Label subtitleLabel = new Label("Kick, ban, and unban people in " + server.getName() + ".");
        subtitleLabel.getStyleClass().add("modal-subtitle");

        VBox memberRows = new VBox(8);
        if (members.isEmpty()) {
            Label empty = new Label("No members found.");
            empty.getStyleClass().add("profile-empty-text");
            memberRows.getChildren().add(empty);
        } else {
            for (UserSummary member : members) {
                Node avatar = createAvatarGraphic(member, "member-avatar", 36);
                Label nameLabel = new Label(displayName(member));
                nameLabel.getStyleClass().add("member-name");
                Label metaLabel = new Label("@" + member.getUsername() + " • " + serverRoleLabel(server, member.getId()));
                metaLabel.getStyleClass().add("invite-target-handle");

                VBox meta = new VBox(2, nameLabel, metaLabel);
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox row = new HBox(10, avatar, meta, spacer);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("member-row");

                if (canModerateServerMember(server, member.getId())) {
                    Button kickButton = createActionChip("Kick", "fas-door-open", "action-chip-neutral");
                    kickButton.setOnAction(event -> {
                        if (!confirmAction(
                            "Kick Member",
                            "Kick " + displayName(member) + "?",
                            "They can rejoin later with a valid invite."
                        )) {
                            return;
                        }
                        try {
                            service.kickMember(server.getId(), member.getId());
                            pageContainer.getChildren().remove(overlay);
                            refreshChatViews();
                            showServerModerationModal();
                        } catch (RuntimeException ex) {
                            showError(rootMessage(ex));
                        }
                    });

                    Button banButton = createActionChip("Ban", "fas-gavel", "action-chip-danger");
                    banButton.setOnAction(event -> {
                        if (!confirmAction(
                            "Ban Member",
                            "Ban " + displayName(member) + "?",
                            "They will need to be unbanned before they can join again."
                        )) {
                            return;
                        }
                        try {
                            service.banMember(server.getId(), member.getId());
                            pageContainer.getChildren().remove(overlay);
                            refreshChatViews();
                            showServerModerationModal();
                        } catch (RuntimeException ex) {
                            showError(rootMessage(ex));
                        }
                    });

                    row.getChildren().addAll(kickButton, banButton);
                }

                memberRows.getChildren().add(row);
            }
        }

        VBox bannedRows = new VBox(8);
        if (bannedUsers.isEmpty()) {
            Label empty = new Label("No banned users.");
            empty.getStyleClass().add("profile-empty-text");
            bannedRows.getChildren().add(empty);
        } else {
            for (UserSummary user : bannedUsers) {
                Node avatar = createAvatarGraphic(user, "member-avatar", 36);
                Label nameLabel = new Label(displayName(user));
                nameLabel.getStyleClass().add("member-name");
                Label metaLabel = new Label("@" + user.getUsername());
                metaLabel.getStyleClass().add("invite-target-handle");

                VBox meta = new VBox(2, nameLabel, metaLabel);
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button unbanButton = createActionChip("Unban", "fas-undo", "action-chip-primary");
                unbanButton.setOnAction(event -> {
                    try {
                        service.unbanMember(server.getId(), user.getId());
                        pageContainer.getChildren().remove(overlay);
                        refreshChatViews();
                        showServerModerationModal();
                    } catch (RuntimeException ex) {
                        showError(rootMessage(ex));
                    }
                });

                HBox row = new HBox(10, avatar, meta, spacer, unbanButton);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("member-row");
                bannedRows.getChildren().add(row);
            }
        }

        VBox content = new VBox(
            12,
            createProfileSectionCard("MEMBERS", memberRows),
            createProfileSectionCard("BANNED USERS", bannedRows)
        );

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("dm-profile-scroll");
        scrollPane.setPrefViewportHeight(460);

        Button backButton = new Button("Back");
        backButton.getStyleClass().addAll("modal-button", "modal-button-cancel");
        backButton.setOnAction(event -> {
            pageContainer.getChildren().remove(overlay);
            showServerSettingsModal();
        });

        HBox actions = new HBox(10, backButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("modal-actions");

        VBox card = new VBox(12, titleLabel, subtitleLabel, scrollPane, actions);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(640);
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().add(card);
        pageContainer.getChildren().add(overlay);
        Platform.runLater(backButton::requestFocus);
    }

    private void showServerAppearanceModal() {
        if (pageContainer == null) {
            return;
        }

        Optional<WorkspaceServer> selected = selectedServer();
        if (selected.isEmpty()) {
            showError("Select a server first");
            return;
        }
        WorkspaceServer server = selected.orElseThrow();
        if (!canManageServerAppearance(server)) {
            showError("Only owner/admin can edit server appearance");
            return;
        }

        final String[] selectedIconBase64 = new String[] {server.getIconImageBase64()};
        final String[] selectedCoverBase64 = new String[] {server.getCoverImageBase64()};

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");

        Label titleLabel = new Label("Server Appearance");
        titleLabel.getStyleClass().add("modal-title");
        Label subtitleLabel = new Label("Set an icon and cover photo for this server.");
        subtitleLabel.getStyleClass().add("modal-subtitle");

        StackPane bannerPreview = new StackPane();
        bannerPreview.getStyleClass().add("server-cover-preview");
        Runnable refreshPreview = () -> bannerPreview.getChildren().setAll(
            createServerCoverPreview(server, selectedIconBase64[0], selectedCoverBase64[0])
        );
        refreshPreview.run();

        Label iconLabel = new Label("SERVER ICON");
        iconLabel.getStyleClass().add("modal-field-label");
        Button chooseIconButton = new Button("Choose Icon");
        chooseIconButton.getStyleClass().addAll("modal-button", "modal-button-primary");
        Button removeIconButton = new Button("Remove Icon");
        removeIconButton.getStyleClass().addAll("modal-button", "modal-button-cancel");

        Label coverLabel = new Label("SERVER COVER");
        coverLabel.getStyleClass().add("modal-field-label");
        Button chooseCoverButton = new Button("Choose Cover");
        chooseCoverButton.getStyleClass().addAll("modal-button", "modal-button-primary");
        Button removeCoverButton = new Button("Remove Cover");
        removeCoverButton.getStyleClass().addAll("modal-button", "modal-button-cancel");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("modal-error");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        chooseIconButton.setOnAction(event -> {
            try {
                String nextIcon = chooseImageBase64("Choose Server Icon", SERVER_ICON_MAX_BYTES, "Server icon");
                if (nextIcon != null) {
                    selectedIconBase64[0] = nextIcon;
                    errorLabel.setManaged(false);
                    errorLabel.setVisible(false);
                    refreshPreview.run();
                }
            } catch (IllegalArgumentException | IOException ex) {
                errorLabel.setText(rootMessage(ex));
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
            }
        });

        removeIconButton.setOnAction(event -> {
            selectedIconBase64[0] = null;
            refreshPreview.run();
        });

        chooseCoverButton.setOnAction(event -> {
            try {
                String nextCover = chooseImageBase64("Choose Server Cover", SERVER_COVER_MAX_BYTES, "Server cover");
                if (nextCover != null) {
                    selectedCoverBase64[0] = nextCover;
                    errorLabel.setManaged(false);
                    errorLabel.setVisible(false);
                    refreshPreview.run();
                }
            } catch (IllegalArgumentException | IOException ex) {
                errorLabel.setText(rootMessage(ex));
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
            }
        });

        removeCoverButton.setOnAction(event -> {
            selectedCoverBase64[0] = null;
            refreshPreview.run();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("modal-button", "modal-button-cancel");
        cancelButton.setOnAction(event -> pageContainer.getChildren().remove(overlay));

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().addAll("modal-button", "modal-button-primary");
        saveButton.setOnAction(event -> {
            try {
                service.updateServerAppearance(server.getId(), selectedIconBase64[0], selectedCoverBase64[0]);
                refreshServers();
                refreshChannels();
                refreshHeader();
                updateContextSidebarMode();
                pageContainer.getChildren().remove(overlay);
            } catch (RuntimeException ex) {
                errorLabel.setText(rootMessage(ex));
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
            }
        });

        HBox iconActions = new HBox(10, chooseIconButton, removeIconButton);
        iconActions.getStyleClass().add("profile-avatar-actions");
        HBox coverActions = new HBox(10, chooseCoverButton, removeCoverButton);
        coverActions.getStyleClass().add("profile-avatar-actions");
        HBox actions = new HBox(10, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("modal-actions");

        VBox card = new VBox(
            12,
            titleLabel,
            subtitleLabel,
            bannerPreview,
            iconLabel,
            iconActions,
            coverLabel,
            coverActions,
            errorLabel,
            actions
        );
        card.getStyleClass().addAll("modal-card", "server-appearance-card");
        card.setMaxWidth(560);
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().add(card);
        pageContainer.getChildren().add(overlay);
        Platform.runLater(saveButton::requestFocus);
    }

    private Node createServerCoverPreview(WorkspaceServer server, String iconBase64, String coverBase64) {
        WorkspaceServer previewServer = new WorkspaceServer(
            server.getId(),
            server.getName(),
            server.getOwnerUserId(),
            server.getMemberUserIds(),
            server.getBannedUserIds(),
            server.getRolesByUserId(),
            server.getCreatedAt(),
            server.getInviteCode(),
            iconBase64,
            coverBase64
        );

        StackPane preview = new StackPane();
        preview.getStyleClass().add("server-cover-preview-surface");

        Image coverImage = decodeBase64Image(coverBase64);
        if (coverImage != null) {
            ImageView coverView = new ImageView(coverImage);
            coverView.setFitWidth(480);
            coverView.setFitHeight(156);
            coverView.setPreserveRatio(false);
            Rectangle clip = new Rectangle(480, 156);
            clip.setArcWidth(22);
            clip.setArcHeight(22);
            coverView.setClip(clip);
            preview.getChildren().add(coverView);
        } else {
            preview.setStyle(serverBannerGradient(server.getName()));
        }

        Region overlay = new Region();
        overlay.getStyleClass().add("server-banner-overlay");

        StackPane iconWrap = new StackPane();
        iconWrap.getStyleClass().add("server-banner-avatar-wrap");
        populateServerIconWrapper(iconWrap, previewServer, "server-banner-avatar", 56, 16);

        Label title = new Label(server.getName());
        title.getStyleClass().add("server-banner-title");

        Label subtitle = new Label("Preview");
        subtitle.getStyleClass().add("server-banner-subtitle");

        VBox meta = new VBox(4, title, subtitle);
        meta.setAlignment(Pos.CENTER_LEFT);

        HBox content = new HBox(14, iconWrap, meta);
        content.setAlignment(Pos.BOTTOM_LEFT);
        content.getStyleClass().add("server-banner-content");

        preview.getChildren().addAll(overlay, content);
        StackPane.setAlignment(content, Pos.BOTTOM_LEFT);
        StackPane.setMargin(content, new Insets(16));
        return preview;
    }

    private String chooseImageBase64(String title, int maxBytes, String label) throws IOException {
        if (pageContainer == null || pageContainer.getScene() == null) {
            return null;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        java.io.File selectedFile = chooser.showOpenDialog(pageContainer.getScene().getWindow());
        if (selectedFile == null) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(selectedFile.toPath());
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException(
                label + " must be smaller than " + String.format(Locale.ROOT, "%.1f", maxBytes / 1_000_000.0) + " MB."
            );
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private void showStatusPickerModal() {
        if (pageContainer == null || service.currentUser().isEmpty()) {
            return;
        }

        UserSummary currentUser = service.currentUser().orElseThrow();
        final UserStatus[] selectedStatus = new UserStatus[] {
            currentUser.getStatus() == null ? UserStatus.ACTIVE : currentUser.getStatus()
        };
        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");

        Label titleLabel = new Label("Set Status");
        titleLabel.getStyleClass().add("modal-title");
        Label subtitleLabel = new Label("Choose how you appear to other users.");
        subtitleLabel.getStyleClass().add("modal-subtitle");

        FontIcon previewIcon = createIcon(statusIconLiteral(selectedStatus[0]), "status-preview-icon");
        previewIcon.getStyleClass().add(statusIconStyleClass(selectedStatus[0]));
        StackPane previewIconWrap = new StackPane(previewIcon);
        previewIconWrap.getStyleClass().add("status-preview-icon-wrap");

        Label previewName = new Label(displayName(currentUser));
        previewName.getStyleClass().add("status-preview-name");
        Label previewState = new Label(statusLabel(selectedStatus[0]));
        previewState.getStyleClass().add("status-preview-state");
        Label previewHint = new Label(statusDescription(selectedStatus[0]));
        previewHint.getStyleClass().add("status-preview-hint");
        previewHint.setWrapText(true);

        VBox previewMeta = new VBox(3, previewName, previewState, previewHint);
        previewMeta.getStyleClass().add("status-preview-meta");

        HBox previewCard = new HBox(14, previewIconWrap, previewMeta);
        previewCard.getStyleClass().add("status-preview-card");
        previewCard.setAlignment(Pos.CENTER_LEFT);

        VBox optionsBox = new VBox(10);
        optionsBox.getStyleClass().add("status-options");
        Map<UserStatus, Button> optionButtons = new HashMap<>();
        Map<UserStatus, FontIcon> optionChecks = new HashMap<>();

        Runnable refreshStatusSelection = () -> {
            previewIcon.getStyleClass().removeAll(
                "status-icon-active",
                "status-icon-idle",
                "status-icon-dnd",
                "status-icon-invisible"
            );
            previewIcon.setIconLiteral(statusIconLiteral(selectedStatus[0]));
            previewIcon.getStyleClass().add(statusIconStyleClass(selectedStatus[0]));
            previewState.setText(statusLabel(selectedStatus[0]));
            previewHint.setText(statusDescription(selectedStatus[0]));

            for (UserStatus status : UserStatus.values()) {
                Button option = optionButtons.get(status);
                FontIcon checkIcon = optionChecks.get(status);
                if (option == null || checkIcon == null) {
                    continue;
                }
                boolean active = status == selectedStatus[0];
                if (active) {
                    if (!option.getStyleClass().contains("selected")) {
                        option.getStyleClass().add("selected");
                    }
                } else {
                    option.getStyleClass().remove("selected");
                }
                checkIcon.setVisible(active);
                checkIcon.setManaged(active);
            }
        };

        for (UserStatus status : UserStatus.values()) {
            FontIcon optionIcon = createIcon(statusIconLiteral(status), "status-option-icon");
            optionIcon.getStyleClass().add(statusIconStyleClass(status));
            StackPane optionIconWrap = new StackPane(optionIcon);
            optionIconWrap.getStyleClass().add("status-option-icon-wrap");

            Label optionTitle = new Label(statusLabel(status));
            optionTitle.getStyleClass().add("status-option-title");
            Label optionDescription = new Label(statusDescription(status));
            optionDescription.getStyleClass().add("status-option-description");
            optionDescription.setWrapText(true);
            VBox optionText = new VBox(2, optionTitle, optionDescription);

            FontIcon checkIcon = createIcon("fas-check", "status-option-check");
            checkIcon.setVisible(false);
            checkIcon.setManaged(false);
            optionChecks.put(status, checkIcon);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox optionRow = new HBox(12, optionIconWrap, optionText, spacer, checkIcon);
            optionRow.setAlignment(Pos.CENTER_LEFT);

            Button optionButton = new Button();
            optionButton.setGraphic(optionRow);
            optionButton.setText(null);
            optionButton.setMaxWidth(Double.MAX_VALUE);
            optionButton.setAlignment(Pos.CENTER_LEFT);
            optionButton.getStyleClass().add("status-option-button");
            optionButton.setOnAction(event -> {
                selectedStatus[0] = status;
                refreshStatusSelection.run();
            });
            optionButtons.put(status, optionButton);
            optionsBox.getChildren().add(optionButton);
        }
        refreshStatusSelection.run();

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("modal-error");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("modal-button", "modal-button-cancel");
        cancelButton.setOnAction(event -> pageContainer.getChildren().remove(overlay));

        Button saveButton = new Button("Save Status");
        saveButton.getStyleClass().addAll("modal-button", "modal-button-primary");
        Runnable submitAction = () -> {
            try {
                service.updateStatus(selectedStatus[0]);
                pageContainer.getChildren().remove(overlay);
            } catch (RuntimeException ex) {
                errorLabel.setText(rootMessage(ex));
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
            }
        };
        saveButton.setOnAction(event -> submitAction.run());

        HBox actions = new HBox(10, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("modal-actions");

        VBox card = new VBox(14, titleLabel, subtitleLabel, previewCard, optionsBox, errorLabel, actions);
        card.getStyleClass().addAll("modal-card", "status-modal-card");
        card.setMaxWidth(560);
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().add(card);
        pageContainer.getChildren().add(overlay);
        Platform.runLater(saveButton::requestFocus);
    }

    private void showReactionPickerModal(Consumer<String> onPick) {
        if (pageContainer == null || onPick == null) {
            return;
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");

        Label titleLabel = new Label("Add Reaction");
        titleLabel.getStyleClass().add("modal-title");
        Label subtitleLabel = new Label("Pick one emoji reaction.");
        subtitleLabel.getStyleClass().add("modal-subtitle");

        TextField searchField = new TextField();
        searchField.setPromptText("Search emoji");
        searchField.getStyleClass().addAll("modal-input", "reaction-picker-search");

        ObservableList<List<EmojiOption>> filteredRows = FXCollections.observableArrayList();
        ListView<List<EmojiOption>> emojiListView = new ListView<>(filteredRows);
        emojiListView.getStyleClass().addAll("modal-list", "reaction-picker-list");
        emojiListView.setPrefHeight(420);

        Label emptyLabel = new Label("No emojis found.");
        emptyLabel.getStyleClass().add("reaction-picker-empty");
        emojiListView.setPlaceholder(emptyLabel);
        emojiListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(List<EmojiOption> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("reaction-picker-row");
                for (EmojiOption option : item) {
                    Button emojiButton = new Button();
                    emojiButton.setFocusTraversable(false);
                    emojiButton.getStyleClass().add("reaction-picker-button");
                    emojiButton.setGraphic(createEmojiGraphic(option.emoji(), 24));
                    emojiButton.setText(null);
                    emojiButton.setAccessibleText(option.name());
                    emojiButton.setTooltip(new Tooltip(option.name()));
                    emojiButton.setOnAction(event -> {
                        pageContainer.getChildren().remove(overlay);
                        onPick.accept(option.emoji());
                    });
                    row.getChildren().add(emojiButton);
                }
                setText(null);
                setGraphic(row);
            }
        });

        Runnable refreshFilter = () -> {
            String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
            List<EmojiOption> matches = new ArrayList<>();
            for (EmojiOption option : EMOJI_CATALOG) {
                if (query.isBlank()
                    || option.name().toLowerCase(Locale.ROOT).contains(query)
                    || option.emoji().contains(query)
                    || option.codepoints().contains(query)) {
                    matches.add(option);
                }
            }
            filteredRows.setAll(chunkEmojiOptions(matches, 8));
        };
        searchField.textProperty().addListener((observable, oldValue, newValue) -> refreshFilter.run());
        refreshFilter.run();

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("modal-button", "modal-button-cancel");
        cancelButton.setOnAction(event -> pageContainer.getChildren().remove(overlay));

        HBox actions = new HBox(10, cancelButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("modal-actions");

        VBox card = new VBox(14, titleLabel, subtitleLabel, searchField, emojiListView, actions);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(460);
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().add(card);
        pageContainer.getChildren().add(overlay);
        Platform.runLater(searchField::requestFocus);
    }

    private static List<EmojiOption> loadEmojiCatalog() {
        try (InputStream stream = HelloApplication.class.getResourceAsStream(
            "/com/discord/discord_lite/emoji/emoji-catalog.tsv"
        )) {
            if (stream == null) {
                return defaultEmojiCatalog();
            }
            List<EmojiOption> catalog = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.split("\t", 3);
                    if (parts.length < 3) {
                        continue;
                    }
                    catalog.add(new EmojiOption(parts[0], parts[1], parts[2]));
                }
            }
            return catalog.isEmpty() ? defaultEmojiCatalog() : List.copyOf(catalog);
        } catch (IOException ex) {
            return defaultEmojiCatalog();
        }
    }

    private static Map<String, String> buildEmojiCodepointMap(List<EmojiOption> catalog) {
        Map<String, String> codepointsByEmoji = new LinkedHashMap<>();
        for (EmojiOption option : catalog) {
            if (option == null || option.emoji() == null || option.emoji().isBlank()) {
                continue;
            }
            codepointsByEmoji.putIfAbsent(option.emoji(), option.codepoints());
        }
        return Map.copyOf(codepointsByEmoji);
    }

    private static List<EmojiOption> defaultEmojiCatalog() {
        return List.of(
            new EmojiOption("👍", "thumbs up", "1f44d"),
            new EmojiOption("❤️", "red heart", "2764-fe0f"),
            new EmojiOption("😂", "face with tears of joy", "1f602"),
            new EmojiOption("😮", "face with open mouth", "1f62e"),
            new EmojiOption("😢", "crying face", "1f622"),
            new EmojiOption("🎉", "party popper", "1f389"),
            new EmojiOption("🔥", "fire", "1f525"),
            new EmojiOption("👏", "clapping hands", "1f44f")
        );
    }

    private static List<List<EmojiOption>> chunkEmojiOptions(List<EmojiOption> options, int rowSize) {
        List<List<EmojiOption>> rows = new ArrayList<>();
        if (rowSize <= 0) {
            rows.add(List.copyOf(options));
            return rows;
        }
        for (int index = 0; index < options.size(); index += rowSize) {
            int end = Math.min(index + rowSize, options.size());
            rows.add(List.copyOf(options.subList(index, end)));
        }
        return rows;
    }

    private void showTextInputModal(
        String title,
        String subtitle,
        String fieldLabel,
        String primaryActionLabel,
        Consumer<String> onSubmit
    ) {
        if (pageContainer == null) {
            return;
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("modal-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("modal-subtitle");

        Label inputLabel = new Label(fieldLabel.toUpperCase());
        inputLabel.getStyleClass().add("modal-field-label");
        TextField inputField = new TextField();
        inputField.getStyleClass().add("modal-input");
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("modal-error");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("modal-button", "modal-button-cancel");
        cancelButton.setOnAction(event -> pageContainer.getChildren().remove(overlay));

        Button submitButton = new Button(primaryActionLabel);
        submitButton.getStyleClass().addAll("modal-button", "modal-button-primary");
        Runnable submitAction = () -> {
            String value = inputField.getText() == null ? "" : inputField.getText().trim();
            if (value.isBlank()) {
                errorLabel.setText(fieldLabel + " is required.");
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
                return;
            }
            pageContainer.getChildren().remove(overlay);
            onSubmit.accept(value);
        };
        submitButton.setOnAction(event -> submitAction.run());
        inputField.setOnAction(event -> submitAction.run());

        HBox actions = new HBox(10, cancelButton, submitButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("modal-actions");

        VBox card = new VBox(
            12,
            titleLabel,
            subtitleLabel,
            inputLabel,
            inputField,
            errorLabel,
            actions
        );
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(500);
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().add(card);
        pageContainer.getChildren().add(overlay);
        Platform.runLater(inputField::requestFocus);
    }

    private void showUserPickerModal(
        String title,
        String subtitle,
        List<UserSummary> users,
        Consumer<UserSummary> onPick
    ) {
        if (pageContainer == null) {
            return;
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("modal-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("modal-subtitle");

        TextField searchField = new TextField();
        searchField.getStyleClass().add("modal-input");
        searchField.setPromptText("Search users");

        ListView<UserSummary> listView = new ListView<>();
        listView.getStyleClass().add("modal-list");
        listView.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(UserSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Node avatar = createAvatarGraphic(item, "member-avatar", 36);
                Label name = new Label(displayName(item));
                name.getStyleClass().add("member-name");
                HBox row = new HBox(10, avatar, name);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("member-row");
                setText(null);
                setGraphic(row);
            }
        });
        listView.getItems().setAll(users);
        listView.setPrefHeight(220);

        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase();
            if (query.isBlank()) {
                listView.getItems().setAll(users);
            } else {
                listView.getItems().setAll(
                    users.stream()
                        .filter(user -> profileSearchText(user).contains(query))
                        .toList()
                );
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("modal-button", "modal-button-cancel");
        cancelButton.setOnAction(event -> pageContainer.getChildren().remove(overlay));

        Button openButton = new Button("Open DM");
        openButton.getStyleClass().addAll("modal-button", "modal-button-primary");
        openButton.setDisable(true);
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            openButton.setDisable(newValue == null);
        });
        Runnable pickAction = () -> {
            UserSummary selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            pageContainer.getChildren().remove(overlay);
            onPick.accept(selected);
        };
        openButton.setOnAction(event -> pickAction.run());
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && listView.getSelectionModel().getSelectedItem() != null) {
                pickAction.run();
            }
        });

        HBox actions = new HBox(10, cancelButton, openButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("modal-actions");

        VBox card = new VBox(12, titleLabel, subtitleLabel, searchField, listView, actions);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(520);
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().add(card);
        pageContainer.getChildren().add(overlay);
        Platform.runLater(searchField::requestFocus);
    }

    private void showCreateChannelModal(List<ChannelGroup> groups, BiConsumer<String, String> onCreate) {
        if (pageContainer == null) {
            return;
        }
        if (groups == null || groups.isEmpty()) {
            showError("No channel groups found.");
            return;
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("modal-overlay");

        Label titleLabel = new Label("Create Channel");
        titleLabel.getStyleClass().add("modal-title");
        Label subtitleLabel = new Label("Choose a channel name and group.");
        subtitleLabel.getStyleClass().add("modal-subtitle");

        Label inputLabel = new Label("CHANNEL NAME");
        inputLabel.getStyleClass().add("modal-field-label");
        TextField nameField = new TextField();
        nameField.getStyleClass().add("modal-input");
        nameField.setPromptText("e.g. announcements");

        Label groupLabel = new Label("CHANNEL GROUP");
        groupLabel.getStyleClass().add("modal-field-label");
        ListView<ChannelGroup> groupList = new ListView<>();
        groupList.getStyleClass().add("modal-list");
        groupList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(ChannelGroup item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        groupList.getItems().setAll(groups);
        groupList.setPrefHeight(140);
        groupList.getSelectionModel().selectFirst();

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("modal-error");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("modal-button", "modal-button-cancel");
        cancelButton.setOnAction(event -> pageContainer.getChildren().remove(overlay));

        Button createButton = new Button("Create");
        createButton.getStyleClass().addAll("modal-button", "modal-button-primary");
        Runnable createAction = () -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            ChannelGroup selectedGroup = groupList.getSelectionModel().getSelectedItem();
            if (name.isBlank()) {
                errorLabel.setText("Channel name is required.");
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
                return;
            }
            if (selectedGroup == null) {
                errorLabel.setText("Choose a channel group.");
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
                return;
            }
            pageContainer.getChildren().remove(overlay);
            onCreate.accept(name, selectedGroup.getId());
        };
        createButton.setOnAction(event -> createAction.run());
        nameField.setOnAction(event -> createAction.run());

        HBox actions = new HBox(10, cancelButton, createButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("modal-actions");

        VBox card = new VBox(
            12,
            titleLabel,
            subtitleLabel,
            inputLabel,
            nameField,
            groupLabel,
            groupList,
            errorLabel,
            actions
        );
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(520);
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().add(card);
        pageContainer.getChildren().add(overlay);
        Platform.runLater(nameField::requestFocus);
    }

    private void applyDialogTheme(Alert alert) {
        if (themeStylesheet == null) {
            return;
        }
        if (!alert.getDialogPane().getStylesheets().contains(themeStylesheet)) {
            alert.getDialogPane().getStylesheets().add(themeStylesheet);
        }
        alert.getDialogPane().getStyleClass().add("discord-dialog");
    }

    private void withSuppressedSelections(Runnable action) {
        suppressSelectionEvents = true;
        try {
            action.run();
        } finally {
            suppressSelectionEvents = false;
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Operation failed" : current.getMessage();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Operation failed");
        alert.setContentText(message);
        applyDialogTheme(alert);
        alert.showAndWait();
    }

    private boolean confirmAction(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        applyDialogTheme(alert);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText("Discord Lite");
        alert.setContentText(message);
        applyDialogTheme(alert);
        alert.showAndWait();
    }

    private record ChannelGroupHeaderRow(ChannelGroup group, boolean collapsed, int channelCount) {
    }

    private record ConversationDateDivider(LocalDate date, String label) {
    }

    private record ConversationUnreadDivider(String label) {
    }

    private record ConversationChannelIntro(Channel channel, WorkspaceServer server) {
    }

    private record ConversationProfileIntro(UserProfileDetails profile) {
    }

    private record MemberSectionHeader(String label, int count) {
    }

    private record ReplyTarget(String conversationKey, String messageId, String senderName, String previewText) {
    }

    private record EditingMessageTarget(String conversationKey, String messageId) {
    }

    private record MentionQuery(int startIndex, int endIndex, String queryText) {
    }

    private record MentionSuggestion(
        MentionSuggestionKind kind,
        String insertText,
        String label,
        String meta,
        UserSummary user
    ) {
    }

    private record RenderableMention(int endIndex, String displayText, MentionSuggestionKind kind) {
    }

    private record EmojiOption(String emoji, String name, String codepoints) {
    }

    private enum MentionSuggestionKind {
        EVERYONE,
        USER
    }

    private enum ConversationMode {
        NONE,
        CHANNEL,
        DM
    }

    private enum AppPage {
        WELCOME,
        LOGIN,
        REGISTER,
        CHAT
    }
}
