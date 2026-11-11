    if-eqz v39, :cond_3f

    const v3, -0x163fd6e

    invoke-virtual {v7, v3}, LN/p;->V(I)V

    invoke-virtual {v7, v0}, LN/p;->f(Ljava/lang/Object;)Z

    move-result v3

    move/from16 v34, v3

    invoke-virtual {v7}, LN/p;->K()Ljava/lang/Object;

    move-result-object v3

    move-wide/from16 v36, v8

    move-object/from16 v8, v51

    if-nez v34, :cond_3d

    if-ne v3, v8, :cond_3e

    :cond_3d
    new-instance v3, LQ3/Q4;

    const/4 v9, 0x7

    invoke-direct {v3, v0, v9}, LQ3/Q4;-><init>(LQ3/T3;I)V

    invoke-virtual {v7, v3}, LN/p;->f0(Ljava/lang/Object;)V

    :cond_3e
    check-cast v3, Ln4/a;

    const/4 v9, 0x0

    invoke-virtual {v7, v9}, LN/p;->p(Z)V

    goto :goto_1e

    :cond_3f
    move-wide/from16 v36, v8

    move-object/from16 v8, v51

    const/4 v9, 0x0

    invoke-virtual {v0}, LQ3/T3;->m()Z

    move-result v3

    if-nez v3, :cond_40

    invoke-virtual {v0}, LQ3/T3;->j()Z

    move-result v3

    if-eqz v3, :cond_40

    const/4 v3, 0x0

    goto :goto_1e

    :cond_40
    move-object/from16 v3, v22

    :goto_1e
    invoke-virtual {v7, v9}, LN/p;->p(Z)V


    # remaining apps
    invoke-virtual {v0}, LQ3/T3;->k()Ljava/util/List;

    move-result-object v5

    invoke-interface {v5}, Ljava/util/List;->isEmpty()Z

    move-result v5

    if-eqz v5, :cond_68

    if-eqz v39, :cond_66

    const v5, -0x1628cae

    invoke-virtual {v7, v5}, LN/p;->V(I)V

    invoke-virtual {v7, v0}, LN/p;->f(Ljava/lang/Object;)Z

    move-result v5

    move-object/from16 v57, v2

    invoke-virtual {v7}, LN/p;->K()Ljava/lang/Object;

    move-result-object v2

    if-nez v5, :cond_64

    if-ne v2, v1, :cond_65

    :cond_64
    new-instance v2, LQ3/Q4;

    const/4 v5, 0x5

    invoke-direct {v2, v0, v5}, LQ3/Q4;-><init>(LQ3/T3;I)V

    invoke-virtual {v7, v2}, LN/p;->f0(Ljava/lang/Object;)V

    :cond_65
    check-cast v2, Ln4/a;

    const/4 v5, 0x0

    invoke-virtual {v7, v5}, LN/p;->p(Z)V

    goto :goto_27

    :cond_66
    move-object/from16 v57, v2

    invoke-virtual {v0}, LQ3/T3;->m()Z

    move-result v2

    if-nez v2, :cond_67

    invoke-virtual {v0}, LQ3/T3;->j()Z

    move-result v2

    if-eqz v2, :cond_67

    goto :goto_28

    :cond_67
    move-object/from16 v2, v51

    :goto_27
    const/4 v5, 0x0

    goto :goto_29

    :cond_68
    move-object/from16 v57, v2

    :goto_28
    const/4 v2, 0x0

    goto :goto_27

    :goto_29
    invoke-virtual {v7, v5}, LN/p;->p(Z)V

