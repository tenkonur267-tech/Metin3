/* Test target: a deliberately game-like pointer chain the scanner must find.
 *
 *   g_root -> Level1 -> Level2 -> Player{hp, mp, gold, x, y}
 *
 * It prints the resolved addresses so the end-to-end test can assert that the
 * chain the tool discovers is the chain that actually exists.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

typedef struct {
    int   hp;
    int   mp;
    int   gold;
    float x;
    float y;
} Player;

typedef struct {
    char   pad[0x18];
    Player *player;   /* at +0x18 */
} Level2;

typedef struct {
    char   pad[0x40];
    Level2 *next;     /* at +0x40 */
} Level1;

/* Lives in the executable's .bss -> a static root at a fixed module offset. */
Level1 *g_root = NULL;

int main(int argc, char **argv) {
    int start_hp = (argc > 1) ? atoi(argv[1]) : 1500;

    Player *p = calloc(1, sizeof(Player));
    Level2 *l2 = calloc(1, sizeof(Level2));
    Level1 *l1 = calloc(1, sizeof(Level1));
    l2->player = p;
    l1->next = l2;
    g_root = l1;

    p->hp = start_hp;
    p->mp = 300;
    p->gold = 123456;
    p->x = 101.5f;
    p->y = -42.25f;

    printf("pid=%d\n", getpid());
    printf("player=%p\n", (void *)p);
    printf("hp=%p\n", (void *)&p->hp);
    printf("gold=%p\n", (void *)&p->gold);
    printf("root=%p\n", (void *)&g_root);
    fflush(stdout);

    /* Wait for the test driver to tell us to change hp, so it can refine. */
    char line[64];
    while (fgets(line, sizeof line, stdin)) {
        if (strncmp(line, "hp ", 3) == 0) {
            p->hp = atoi(line + 3);
        } else if (strncmp(line, "quit", 4) == 0) {
            break;
        }
        printf("ok hp=%d\n", p->hp);
        fflush(stdout);
    }
    return 0;
}
