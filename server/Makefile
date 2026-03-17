.DEFAULT_GOAL := help

.PHONY: help up down tail logs restart update status enable disable

CONFIG_FILE := .xword.env
CONFIG_TEMP_FILE := $(CURDIR)/$(CONFIG_FILE).tmp
INSTALL_SCRIPT := $(CURDIR)/install.sh
DEFAULT_INSTALL_REF := master
DEFAULT_RESTART_POLICY := unless-stopped
DISABLED_RESTART_POLICY := no
VERSION_FILE_NAME := version.txt
CURRENT_VERSION_FILE := $(CURDIR)/$(VERSION_FILE_NAME)
PARENT_VERSION_FILE := $(abspath $(CURDIR)/../$(VERSION_FILE_NAME))
SOURCE_TREE_GIT_DIR := $(CURDIR)/../.git
SOURCE_TREE_APP_DIR := $(CURDIR)/../app/src
SOURCE_TREE_COMPOSE_FILE := $(CURDIR)/../compose.yaml
CONTAINER_NAME := words-server
LOG_TAIL_LINES := 0

-include $(CONFIG_FILE)

XWORD_REF ?= $(DEFAULT_INSTALL_REF)
XWORD_RESTART_POLICY ?= $(DEFAULT_RESTART_POLICY)

define require_docker_command
if ! command -v docker >/dev/null 2>&1; then \
	echo "Missing required command: docker" >&2; \
	exit 1; \
fi
endef

define resolve_version_file
version_file=""; \
if [ -f "$(CURRENT_VERSION_FILE)" ]; then \
	version_file="$(CURRENT_VERSION_FILE)"; \
elif [ -f "$(PARENT_VERSION_FILE)" ]; then \
	version_file="$(PARENT_VERSION_FILE)"; \
else \
	echo "Missing $(VERSION_FILE_NAME). Expected $(CURRENT_VERSION_FILE) or $(PARENT_VERSION_FILE)." >&2; \
	exit 1; \
fi
endef

define docker_compose_with_version
$(call require_docker_command); \
$(call resolve_version_file); \
XWORD_RESTART_POLICY="$(XWORD_RESTART_POLICY)" WORDS_VERSION_FILE_HOST="$$version_file" docker compose $(1)
endef

define require_flat_install_root
if [ -d "$(SOURCE_TREE_GIT_DIR)" ] && [ -d "$(SOURCE_TREE_APP_DIR)" ] && [ -f "$(SOURCE_TREE_COMPOSE_FILE)" ]; then \
	echo "Refusing to run make update from the repository source checkout. Run it from an installed server directory instead." >&2; \
	exit 1; \
fi; \
if [ ! -f "$(INSTALL_SCRIPT)" ]; then \
	echo "Missing $(INSTALL_SCRIPT)." >&2; \
	exit 1; \
fi
endef

define set_config_value
if [ -f "$(CONFIG_FILE)" ]; then \
	grep -v '^$(1)=' "$(CONFIG_FILE)" > "$(CONFIG_TEMP_FILE)" || true; \
else \
	: > "$(CONFIG_TEMP_FILE)"; \
fi; \
printf '%s=%s\n' "$(1)" "$(2)" >> "$(CONFIG_TEMP_FILE)"; \
mv "$(CONFIG_TEMP_FILE)" "$(CONFIG_FILE)"
endef

define update_restart_policy_for_existing_container
if docker container inspect "$(CONTAINER_NAME)" >/dev/null 2>&1; then \
	docker update --restart="$(1)" "$(CONTAINER_NAME)" >/dev/null; \
fi
endef

help:
	@printf '%s\n' 'Server commands:'
	@printf '  %-10s %s\n' 'help' 'Show this help.'
	@printf '  %-10s %s\n' 'up' 'Build and start the server container.'
	@printf '  %-10s %s\n' 'down' 'Stop and remove the server container.'
	@printf '  %-10s %s\n' 'restart' 'Recreate the server container.'
	@printf '  %-10s %s\n' 'logs' 'Follow server logs.'
	@printf '  %-10s %s\n' 'tail' 'Alias for logs.'
	@printf '  %-10s %s\n' 'status' 'Show container state, restart policy, and active version file.'
	@printf '  %-10s %s\n' 'enable' 'Set restart policy to $(DEFAULT_RESTART_POLICY) for $(CONTAINER_NAME).'
	@printf '  %-10s %s\n' 'disable' 'Set restart policy to $(DISABLED_RESTART_POLICY) for $(CONTAINER_NAME) and stop it.'
	@printf '  %-10s %s\n' 'update' 'Reinstall server files from GitHub and restart (installed layout only).'
	@printf '%s\n' ''
	@printf '%s\n' 'Notes:'
	@printf '  %s\n' 'The server expects version.txt in the current directory or its parent directory.'
	@printf '  %s\n' 'make update is intended for directories created by install.sh, not for the repository checkout.'

up:
	@$(call docker_compose_with_version,up -d --build)

down:
	@$(call docker_compose_with_version,down)

logs:
	@$(call docker_compose_with_version,logs -f --tail=$(LOG_TAIL_LINES))

tail:
	@$(MAKE) logs

restart:
	@$(call docker_compose_with_version,up -d --build --force-recreate)

update:
	@$(call require_flat_install_root)
	@XWORD_REF="$(XWORD_REF)" sh "$(INSTALL_SCRIPT)"
	@$(MAKE) restart

status:
	@$(call require_docker_command); \
	$(call resolve_version_file); \
	version_value="$$(tr -d '\r\n' < "$$version_file")"; \
	if [ -z "$$version_value" ]; then \
		echo "Version file is empty: $$version_file" >&2; \
		exit 1; \
	fi; \
	container_state="$$(docker container inspect -f '{{.State.Status}}' "$(CONTAINER_NAME)" 2>/dev/null || printf '%s' 'not_created')"; \
	container_restart_policy="$$(docker container inspect -f '{{.HostConfig.RestartPolicy.Name}}' "$(CONTAINER_NAME)" 2>/dev/null || printf '%s' 'not_created')"; \
	printf 'container=%s\n' "$(CONTAINER_NAME)"; \
	printf 'state=%s\n' "$$container_state"; \
	printf 'configured_restart_policy=%s\n' "$(XWORD_RESTART_POLICY)"; \
	printf 'container_restart_policy=%s\n' "$$container_restart_policy"; \
	printf 'version_file=%s\n' "$$version_file"; \
	printf 'version=%s\n' "$$version_value"

enable:
	@$(call require_docker_command)
	@$(call set_config_value,XWORD_RESTART_POLICY,$(DEFAULT_RESTART_POLICY))
	@$(call update_restart_policy_for_existing_container,$(DEFAULT_RESTART_POLICY))
	@echo "Configured restart policy: $(DEFAULT_RESTART_POLICY)"

disable:
	@$(call require_docker_command)
	@$(call set_config_value,XWORD_RESTART_POLICY,$(DISABLED_RESTART_POLICY))
	@$(call update_restart_policy_for_existing_container,$(DISABLED_RESTART_POLICY))
	@if docker container inspect "$(CONTAINER_NAME)" >/dev/null 2>&1; then \
		docker stop "$(CONTAINER_NAME)" >/dev/null 2>&1 || true; \
	fi
	@echo "Configured restart policy: $(DISABLED_RESTART_POLICY)"
